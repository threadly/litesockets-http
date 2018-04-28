package org.threadly.litesockets.client.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.threadly.concurrent.ReschedulingOperation;
import org.threadly.concurrent.SingleThreadScheduler;
import org.threadly.concurrent.SubmitterScheduler;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.Client.ClientCloseListener;
import org.threadly.litesockets.Client.Reader;
import org.threadly.litesockets.NoThreadSocketExecuter;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.TCPClient;
import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.buffers.ReuseableMergedByteBuffers;
import org.threadly.litesockets.protocols.http.request.ClientHTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseProcessor;
import org.threadly.litesockets.protocols.http.response.HTTPResponseProcessor.HTTPResponseCallback;
import org.threadly.litesockets.protocols.http.shared.HTTPAddress;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;
import org.threadly.litesockets.protocols.http.shared.HTTPRequestType;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.litesockets.protocols.ws.WebSocketFrameParser.WebSocketFrame;
import org.threadly.litesockets.utils.IOUtils;
import org.threadly.litesockets.utils.SSLUtils;
import org.threadly.util.AbstractService;
import org.threadly.util.Clock;
import org.threadly.util.Pair;

/**
 * <p>This is a HTTPClient for doing many simple HTTPRequests.  Every request will be make a new connection and requests
 * can be done in parallel.  This is mainly used for doing many smaller Request and Response messages as the full Request/Response 
 * is kept in memory and are not handled as streams.  See {@link HTTPStreamClient} for use with large HTTP data sets.</p>
 */
public class HTTPClient extends AbstractService {
  public static final int DEFAULT_CONCURRENT = 2;
  public static final int DEFAULT_TIMEOUT = 15000;
  public static final int DEFAULT_MAX_IDLE = 45000;
  public static final int MAX_HTTP_RESPONSE = 1048576;  //1MB

  private final int maxResponseSize;
  private final SubmitterScheduler ssi;
  private final SocketExecuter sei;
  private final ConcurrentLinkedQueue<HTTPRequestWrapper> queue = new ConcurrentLinkedQueue<>();
  private final ConcurrentHashMap<TCPClient, HTTPRequestWrapper> inProcess = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<HTTPAddress, ArrayDeque<Pair<Long,TCPClient>>> sockets = new ConcurrentHashMap<>();
  private final CopyOnWriteArraySet<TCPClient> tcpClients = new CopyOnWriteArraySet<>();
  private final MainClientProcessor mcp = new MainClientProcessor();
  private final RunSocket runSocketTask;
  private final int maxConcurrent;
  private volatile Runnable checkIdle = null;
  private volatile long defaultTimeoutMS = HTTPRequest.DEFAULT_TIMEOUT_MS;
  private volatile SSLContext sslContext = SSLUtils.OPEN_SSL_CTX;
  private volatile long maxIdleTime = DEFAULT_MAX_IDLE;

  private NoThreadSocketExecuter ntse = null;
  private SingleThreadScheduler sts = null;

  /**
   * <p>This is the default constructor it will create its own {@link SingleThreadScheduler} to use as a threadpool, as
   * well as using the default {@value DEFAULT_CONCURRENT} and {@value MAX_HTTP_RESPONSE}.  This means we will do at most
   * 2 HTTPRequests at the same time, and those responses can be up to 1mb in size.</p>
   * 
   */
  public HTTPClient() {
    this(DEFAULT_CONCURRENT, MAX_HTTP_RESPONSE);
  }

  /**
   * <p>This constructor will let you set the max Concurrent Requests and max Response Size but will still 
   * create its own {@link SingleThreadScheduler} to use as a threadpool.</p>
   *  
   * @param maxConcurrent maximum number of requests to run simultaneously. 
   * @param maxResponseSize the maximum responseSize clients are allowed to send.
   */
  public HTTPClient(int maxConcurrent, int maxResponseSize) {
    this.maxConcurrent = maxConcurrent;
    this.maxResponseSize = maxResponseSize;
    sts = new SingleThreadScheduler();
    this.ssi = sts;
    ntse = new NoThreadSocketExecuter();
    sei = ntse;
    runSocketTask = new RunSocket(ssi);
  }

  /**
   * <p>This constructor will let you set the max Concurrent Requests and max Response Size
   * as well as your own {@link SocketExecuter} as the thread pool to use.</p> 
   * 
   * @param maxConcurrent maximum number of requests to run simultaneously. 
   * @param maxResponseSize the maximum responseSize clients are allowed to send.
   * @param sei the SocketExecuter to use with these HTTPClients.
   */
  public HTTPClient(int maxConcurrent, int maxResponseSize, SocketExecuter sei) {
    this.maxConcurrent = maxConcurrent;
    this.maxResponseSize = maxResponseSize;
    this.ssi = sei.getThreadScheduler();
    this.sei = sei;
    runSocketTask = new RunSocket(ssi);
  }
 
  /**
   * Number of HTTPRequests pending on the HTTPClient.  These requests are not currently being processed, 
   * but waiting in queue for the next free http worker.
   * 
   * @return number of pending requests.
   */
  public int getRequestQueueSize() {
    return this.queue.size();
  }

  /**
   * Number of HTTPRequests pending on the HTTPClient.  These are requests that are currently 
   * either trying to connect to or have been sent to a server.
   * 
   * @return number of request currently in progress.
   */
  public int getInProgressSize() {
    return this.inProcess.size();
  }

  /**
   * Returns the total number of open Client Connections on this HTTPClient.
   * 
   * @return number of open connections.
   */
  public int getOpenConnections() {
    return tcpClients.size();
  }
  
  /**
   * Sets the {@link SSLContext} to be used for connection using ssl on this client.
   * If nothing is set a completely open {@link SSLContext} is used providing no cert validation.
   * 
   * @param sslctx the {@link SSLContext} to use for ssl connections. 
   */
  public void setSSLContext(SSLContext sslctx) {
    sslContext = sslctx;
  }
  
  /**
   * This forces closed all client connections on this HTTPClient.
   * NOTE: this will disrupt any pending requests if called.
   * 
   */
  public void closeAllClients() {
    for(TCPClient client: tcpClients) {
      client.close();
    }
  }

  /**
   * Sets the default timeout in milliseconds to wait for HTTPRequest responses from the server.
   * 
   * @param timeout time in milliseconds to wait for HTTPRequests to finish.
   */
  public void setTimeout(long timeout, TimeUnit unit) {
    this.defaultTimeoutMS = Math.min(Math.max(unit.toMillis(timeout),HTTPRequest.MIN_TIMEOUT_MS), HTTPRequest.MAX_TIMEOUT_MS);
  }
  
  public long getMaxIdleTimeout() {
    return this.maxIdleTime;
  }

  /**
   * Sets the max amount of time we will hold onto idle connections.  A 0 means we close connections when done, less
   * than zero means we will never expire connections.
   * 
   * @param it the time in milliseconds to wait before timing out a connection.
   */
  public void setMaxIdleTimeout(long it, TimeUnit unit) {
    this.maxIdleTime = unit.toMillis(it);
    if(this.maxIdleTime > 0) {
      this.checkIdle = new Runnable() {
        @Override
        public void run() {
          if(checkIdle == this) {
            checkIdleSockets();
            ssi.schedule(this, Math.max(100, maxIdleTime/2));
          }
        }
      };
      this.ssi.schedule(checkIdle, Math.max(100, maxIdleTime/2));
    }
  }

  /**
   * Sends a blocking HTTP request.
   * 
   * @param url the url to send the request too.
   * @return an {@link HTTPResponseData} object containing the headers and content of the response.
   * @throws HTTPParsingException is thrown if the server sends back protocol or a response that is larger then allowed.
   */
  public HTTPResponseData request(final URL url) throws HTTPParsingException {
    return request(url, HTTPRequestType.GET, IOUtils.EMPTY_BYTEBUFFER);
  }
  
  /**
   * Sends a blocking HTTP request.
   * 
   * @param url the url to send the request too.
   * @param rt the {@link HTTPRequestType} to use on the request.
   * @param bb the data to put in the body for this request.
   * @return an {@link HTTPResponseData} object containing the headers and content of the response.
   * @throws HTTPParsingException is thrown if the server sends back protocol or a response that is larger then allowed.
   */
  public HTTPResponseData request(final URL url, final HTTPRequestType rt, final ByteBuffer bb) throws HTTPParsingException {
    HTTPResponseData hr = null;
    try {
      hr = requestAsync(url, rt, bb).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      if(e.getCause() instanceof HTTPParsingException) {
        throw (HTTPParsingException)e.getCause();
      } else {
        throw new HTTPParsingException(e);
      }
    }
    return hr;
  }

  /**
   * Sends a blocking HTTP request.
   * 
   * @param ha the {@link HTTPAddress} to connect to, any hostname in the actual HTTPRequest will just be sent in the protocol. 
   * @param request the {@link HTTPRequest} to send the server once connected.
   * @param body the body to send with this request.  You must have set the {@link HTTPRequest} correctly for this body.
   * @param unit the time unit of the timeout argument 
   * @param timeout the maximum time to wait
   * @return an {@link HTTPResponseData} object containing the headers and content of the response.
   * @throws HTTPParsingException is thrown if the server sends back protocol or a response that is larger then allowed.
   */
  public HTTPResponseData request(final ClientHTTPRequest request) throws HTTPParsingException {
    HTTPResponseData hr = null;
    try {
      hr = requestAsync(request).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      if(e.getCause() instanceof HTTPParsingException) {
        throw (HTTPParsingException)e.getCause();
      } else if(e instanceof CancellationException) {
        throw new HTTPParsingException("HTTP Timeout!", e);
      } else {
        throw new HTTPParsingException(e);
      }
    }
    return hr;
  }

  /**
   * Sends an asynchronous HTTP request.
   * 
   * @param url the {@link URL} to send the request too.
   * @return an {@link ListenableFuture} containing a {@link HTTPResponseData} object that will be completed when the request is finished, 
   * successfully or with errors.
   */
  public ListenableFuture<HTTPResponseData> requestAsync(final URL url) {
    return requestAsync(url, HTTPRequestType.GET, IOUtils.EMPTY_BYTEBUFFER);
  }
  
  /**
   * Sends an asynchronous HTTP request.
   * 
   * @param url the {@link URL} to send the request too.
   * @param rt the {@link HTTPRequestType} to use on the request.
   * @param bb the data to put in the body for this request.
   * @return an {@link ListenableFuture} containing a {@link HTTPResponseData} object that will be completed when the request is finished, 
   * successfully or with errors.
   */
  public ListenableFuture<HTTPResponseData> requestAsync(final URL url, final HTTPRequestType rt, final ByteBuffer bb) {
    HTTPRequestBuilder hrb = new HTTPRequestBuilder(url);
    hrb.setRequestType(rt);
    hrb.setTimeout(this.defaultTimeoutMS, TimeUnit.MILLISECONDS);
    if (bb != null && bb.hasRemaining()) {
      hrb.setBody(bb);
    }
    return requestAsync(hrb.buildClientHTTPRequest());
  }

  /**
   * Sends an asynchronous HTTP request.
   * 
   * @param ha the {@link HTTPAddress} to connect to, any hostname in the actual HTTPRequest will just be sent in the protocol. 
   * @param request the {@link HTTPRequest} to send the server once connected.
   * @param body the body to send with this request.  You must have set the {@link HTTPRequest} correctly for this body.
   * @param unit the time unit of the timeout argument 
   * @param timeout the maximum time to wait
   * @return an {@link ListenableFuture} containing a {@link HTTPResponseData} object that will be completed when the request is finished, 
   * successfully or with errors.
   */
  public ListenableFuture<HTTPResponseData> requestAsync(final ClientHTTPRequest request) {
    HTTPRequestWrapper hrw = new HTTPRequestWrapper(request);
    final ListenableFuture<HTTPResponseData> lf = hrw.slf;
    queue.add(hrw);
    if(ntse != null) {
      ntse.wakeup();
      runSocketTask.signalToRun();
    } else {
      processQueue();
    }
    return lf;
  }

  private void processQueue() {
    //This should be done after we do a .select on the ntse to check for more jobs before it exits.
    HTTPRequestWrapper hrw;
    while(maxConcurrent > inProcess.size() && (hrw = queue.poll()) != null) {
      process(hrw);
    }
  }
  
  private void process(HTTPRequestWrapper hrw) {
    if(hrw != null) {
      try {
        sei.watchFuture(hrw.slf, hrw.timeTillExpired()+1);
        
        hrw.updateReadTime();
        hrw.client = getTCPClient(hrw.chr.getHTTPAddress());
        inProcess.put(hrw.client, hrw);
        hrw.client.write(hrw.chr.getHTTPRequest().getByteBuffer());
        hrw.client.write(hrw.chr.getBodyBuffer().duplicate());
      } catch (Exception e) {
        //Have to catch all here or we dont keep processing if NoThreadSE is in use
        //hrw.slf.setFailure(e);
      }
    }
  }

  @Override
  protected void startupService() {
    if (ntse != null) {
      ntse.start();
    }
    setMaxIdleTimeout(this.maxIdleTime, TimeUnit.MILLISECONDS);
  }

  @Override
  protected void shutdownService() {
    if(ntse != null) {
      ntse.stop();
    }
    if(sts != null) {
      sts.shutdownNow();
      try {
        sts.awaitTermination();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private TCPClient getTCPClient(final HTTPAddress ha) throws IOException {
    ArrayDeque<Pair<Long,TCPClient>> pl = sockets.get(ha);
    TCPClient tc = null;
    if(pl != null) {
      synchronized(pl) {
        while(pl.size() > 0 && tc == null) {
          if(pl.peek().getRight().isClosed()) {
            pl.pop();
          } else {
            tc = pl.pop().getRight();
          }
        }
        if(pl.size() == 0) {
          sockets.remove(ha);
        }
      }
    }
    if(tc == null) {
      tc = sei.createTCPClient(ha.getHost(), ha.getPort());
      tcpClients.add(tc);
      if(ha.getdoSSL()) {
        SSLEngine sse = sslContext.createSSLEngine(ha.getHost(), ha.getPort());
        sse.setUseClientMode(true);
        tc.setSSLEngine(sse);
        tc.startSSL();
      }
      tc.setReader(mcp);
      tc.addCloseListener(mcp);
      tc.connect();
    }
    return tc;
  }

  private void addBackTCPClient(final HTTPAddress ha, final TCPClient client) {
    if(maxIdleTime == 0) {
      client.close();
      return;
    }
    if(!client.isClosed()) {
      ArrayDeque<Pair<Long,TCPClient>> ll = sockets.get(ha);  
      if(ll == null) {
        sockets.put(ha, new ArrayDeque<>(8));
        ll = sockets.get(ha);
      }
      synchronized(ll) {
        ll.add(new Pair<>(Clock.lastKnownForwardProgressingMillis(), client));
      }
    }
  }
  
  private void checkIdleSockets() {
    if(maxIdleTime > 0) {
      for(ArrayDeque<Pair<Long,TCPClient>> adq: sockets.values()) {
        synchronized(adq) {
          Iterator<Pair<Long,TCPClient>> iter = adq.iterator();
          while(iter.hasNext()) {
            Pair<Long,TCPClient> c = iter.next();
            if(Clock.lastKnownForwardProgressingMillis() - c.getLeft() > maxIdleTime) {
              iter.remove();
              c.getRight().close();
            }
          }
        }
      }
    }
  }

  /**
   * Used to run the NoThreadSocketExecuter.
   */
  private class RunSocket extends ReschedulingOperation {
    protected RunSocket(SubmitterScheduler scheduler) {
      super(scheduler, 0);
    }

    @Override
    public void run() {
      if(ntse.isRunning()) {
        ntse.select(100);
      }
      if(ntse.isRunning()) {
        processQueue();
        if (! queue.isEmpty() || ! inProcess.isEmpty()) {
          signalToRun();  // still more to run
        }
      }
    }
  }

  /**
   * Class for accepting data into the request processor as well handling the close event.
   */
  private class MainClientProcessor implements Reader, ClientCloseListener {
    @Override
    public void onClose(Client client) {
      HTTPRequestWrapper hrw = inProcess.get(client);

      client.close();
      if(hrw != null) {
        boolean wasProcessing = hrw.hrp.isProcessing();
        hrw.hrp.connectionClosed();
        if(! hrw.slf.isDone() && ! wasProcessing) {
          hrw.client = null;
          process(hrw);  
        } else {
          hrw.slf.setFailure(new HTTPParsingException("Did not get complete body!"));
        }
      }
      inProcess.remove(client);
      tcpClients.remove(client);
    }

    @Override
    public void onRead(Client client) {
      HTTPRequestWrapper hrw = inProcess.get(client);
      if(hrw != null) {
        hrw.hrp.processData(client.getRead());
      } else {
        client.close();
      }
    }
  }

  /**
   * 
   */
  private class HTTPRequestWrapper implements HTTPResponseCallback {
    private final SettableListenableFuture<HTTPResponseData> slf = new SettableListenableFuture<>(false);
    private final HTTPResponseProcessor hrp = new HTTPResponseProcessor();
    private final ClientHTTPRequest chr;
    private HTTPResponse response;
    private ReuseableMergedByteBuffers responseMBB = new ReuseableMergedByteBuffers();
    private TCPClient client;
    private long lastRead = Clock.lastKnownForwardProgressingMillis();

    public HTTPRequestWrapper(ClientHTTPRequest chr) {
      hrp.addHTTPResponseCallback(this);
      this.chr = chr;
    }

    public void updateReadTime() {
      lastRead = Clock.lastKnownForwardProgressingMillis();
    }

    public long timeTillExpired() {
      return chr.getTimeoutMS() - (Clock.lastKnownForwardProgressingMillis() - lastRead);
    }

    @Override
    public void headersFinished(HTTPResponse hr) {
      response = hr;
    }

    @Override
    public void bodyData(ByteBuffer bb) {
      responseMBB.add(bb);
      if(responseMBB.remaining() > maxResponseSize) {
        slf.setFailure(new HTTPParsingException("Response Body to large!"));
        client.close();
      }
    }

    @Override
    public void finished() {
      slf.setResult(new HTTPResponseData(HTTPClient.this, chr.getHTTPRequest(), response, responseMBB.duplicateAndClean()));
      hrp.removeHTTPResponseCallback(this);
      inProcess.remove(client);
      addBackTCPClient(chr.getHTTPAddress(), client);
      processQueue();
    }

    @Override
    public void hasError(Throwable t) {
      slf.setFailure(t);
      client.close();
    }

    @Override
    public void websocketData(WebSocketFrame wsf, ByteBuffer bb) {
      slf.setFailure(new Exception("HTTPClient does not currently support websockets!"));
      client.close();
    }
  }

  /**
   * This is a simple, full HttpResponse with data.
   */
  public static class HTTPResponseData {
    private final HTTPResponse hr;
    private final HTTPRequest origRequest;
    private final MergedByteBuffers body;
    private final HTTPClient client;

    public HTTPResponseData(HTTPClient client, HTTPRequest origRequest, HTTPResponse hr, MergedByteBuffers bb) {
      this.client = client;
      this.hr = hr;
      this.body = bb;
      this.origRequest = origRequest;
    }
    
    public HTTPClient getHTTPClient() {
      return client;
    }
    
    public HTTPRequest getHTTPRequest() {
      return origRequest;
    }

    public HTTPResponse getResponse() {
      return hr;
    }
    
    public HTTPResponseCode getResponseCode() {
      return hr.getResponseHeader().getResponseCode();
    }
        
    public long getContentLength() {
      return body.remaining();
    }

    public MergedByteBuffers getBody() {
      return body.duplicate();
    }

    public String getBodyAsString() {
      return body.duplicate().getAsString(body.remaining());
    }
    
    public InputStream getBodyAsInputStream() {
      return body.duplicate().asInputStream();
    }
    
    @Override
    public String toString() {
      return hr.toString().replaceAll("\r\n", "\\r\\n")+"BodySize:"+body.remaining();
    }
  }
}

