package org.threadly.litesockets.client.http;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.threadly.concurrent.SingleThreadScheduler;
import org.threadly.concurrent.SubmitterScheduler;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.Client.CloseListener;
import org.threadly.litesockets.Client.Reader;
import org.threadly.litesockets.NoThreadSocketExecuter;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.TCPClient;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseProcessor;
import org.threadly.litesockets.protocols.http.response.HTTPResponseProcessor.HTTPResponseCallback;
import org.threadly.litesockets.protocols.http.shared.HTTPAddress;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;
import org.threadly.litesockets.utils.MergedByteBuffers;
import org.threadly.litesockets.utils.SSLUtils;
import org.threadly.util.AbstractService;
import org.threadly.util.Clock;

/**
 * <p>This is a HTTPClient for doing many simple HTTPRequests.  Every request will be make a new connection and requests
 * can be done in parallel.  This is mainly used for doing many smaller Request and Response messages as the full Request/Response 
 * is kept in memory and are not handled as streams.  See {@link HTTPStreamClient} for use with large HTTP data sets.</p>   
 * 
 */
public class HTTPClient extends AbstractService {
  public static final int DEFAULT_CONCURRENT = 2;
  public static final int DEFAULT_TIMEOUT = 15000;
  public static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0); 
  public static final int MAX_HTTP_RESPONSE = 1048576;  //1MB

  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final int maxResponseSize;
  private final SubmitterScheduler ssi;
  private final SocketExecuter sei;
  private final ConcurrentLinkedQueue<HTTPRequestWrapper> queue = new ConcurrentLinkedQueue<HTTPRequestWrapper>();
  private final ConcurrentHashMap<TCPClient, HTTPRequestWrapper> inProcess = new ConcurrentHashMap<TCPClient, HTTPRequestWrapper>();
  private final ConcurrentHashMap<HTTPAddress, ArrayDeque<TCPClient>> sockets = new ConcurrentHashMap<HTTPAddress, ArrayDeque<TCPClient>>();
  private final CopyOnWriteArraySet<TCPClient> tcpClients = new CopyOnWriteArraySet<TCPClient>();
  private final MainClientProcessor mcp = new MainClientProcessor();
  private final int maxConcurrent;
  private volatile int defaultTimeout = DEFAULT_TIMEOUT;
  private volatile SSLContext sslContext = SSLUtils.OPEN_SSL_CTX;

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
    ntse.start();
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
  }
 
  /**
   * Number of HTTPRequests pending on the HTTPClient.  These requests are not currently being processed, but waiting in queue for the next
   * free http worker.
   * 
   * @return number of pending requests.
   */
  public int getRequestQueueSize() {
    return this.queue.size();
  }

  /**
   * Number of HTTPRequests pending on the HTTPClient.  These are requests that are currently either trying to connect to or have been sent to 
   * a server.
   * 
   * @return number of request currently in progress.
   */
  public int getInProgressSize() {
    return this.inProcess.size();
  }

  /**
   * Returns the total number of open Client Connections on this HTTPClient  
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
  public void setTimeout(int timeout) {
    this.defaultTimeout = timeout;
  }

  /**
   * Sends a blocking HTTP request.
   * 
   * @param url the url to send the request too.
   * @return an {@link HTTPResponseData} object containing the headers and content of the response.
   * @throws HTTPParsingException is thrown if the server sends back protocol or a response that is larger then allowed.
   */
  public HTTPResponseData request(final URL url) throws HTTPParsingException {
    HTTPResponseData hr = null;
    try {
      hr = requestAsync(url).get();
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
   * @return an {@link HTTPResponseData} object containing the headers and content of the response.
   * @throws HTTPParsingException is thrown if the server sends back protocol or a response that is larger then allowed.
   */
  public HTTPResponseData request(final HTTPAddress ha, final HTTPRequest request) throws HTTPParsingException{
    return request(ha, request, EMPTY_BUFFER);
  }

  /**
   * Sends a blocking HTTP request.
   * 
   * @param ha the {@link HTTPAddress} to connect to, any hostname in the actual HTTPRequest will just be sent in the protocol. 
   * @param request the {@link HTTPRequest} to send the server once connected.
   * @param body the body to send with this request.  You must have set the {@link HTTPRequest} correctly for this body. 
   * @return an {@link HTTPResponseData} object containing the headers and content of the response.
   * @throws HTTPParsingException is thrown if the server sends back protocol or a response that is larger then allowed.
   */
  public HTTPResponseData request(final HTTPAddress ha, final HTTPRequest request, final ByteBuffer body) throws HTTPParsingException {
    return request(ha, request, body, TimeUnit.MILLISECONDS, defaultTimeout);
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
  public HTTPResponseData request(final HTTPAddress ha, final HTTPRequest request, final ByteBuffer body, final TimeUnit unit, final long timeout) 
      throws HTTPParsingException {
    HTTPResponseData hr = null;
    try {
      hr = requestAsync(ha, request, body, unit, timeout).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      if(e.getCause() instanceof HTTPParsingException) {
        throw (HTTPParsingException)e.getCause();
      } else {
        throw new HTTPParsingException(e.getCause());
      }
    }
    return hr;
  }

  /**
   * Sends an asynchronous HTTP request.
   * 
   * @param url the url to send the request too.
   * @return an {@link ListenableFuture} containing a {@link HTTPResponseData} object that will be completed when the request is finished, 
   * successfully or with errors.
   */
  public ListenableFuture<HTTPResponseData> requestAsync(URL url) {
    boolean ssl = false;
    int port = HTTPConstants.DEFAULT_HTTP_PORT;
    String host = url.getHost();
    if(url.getProtocol().equalsIgnoreCase("https")) {
      port = HTTPConstants.DEFAULT_HTTPS_PORT;
      ssl = true;
    }
    if(url.getPort() != -1) {
      port = url.getPort();
    }
    return requestAsync(new HTTPAddress(host, port, ssl), new HTTPRequestBuilder(url).build());
  }

  /**
   * Sends an asynchronous HTTP request.
   * 
   * @param ha the {@link HTTPAddress} to connect to, any hostname in the actual HTTPRequest will just be sent in the protocol. 
   * @param request the {@link HTTPRequest} to send the server once connected.
   * @return an {@link ListenableFuture} containing a {@link HTTPResponseData} object that will be completed when the request is finished, 
   * successfully or with errors.
   */
  public ListenableFuture<HTTPResponseData> requestAsync(final HTTPAddress ha, final HTTPRequest request) {
    return requestAsync(ha, request, EMPTY_BUFFER);
  }

  /**
   * Sends an asynchronous HTTP request.
   * 
   * @param ha the {@link HTTPAddress} to connect to, any hostname in the actual HTTPRequest will just be sent in the protocol. 
   * @param request the {@link HTTPRequest} to send the server once connected.
   * @param body the body to send with this request.  You must have set the {@link HTTPRequest} correctly for this body. 
   * @return an {@link ListenableFuture} containing a {@link HTTPResponseData} object that will be completed when the request is finished, 
   * successfully or with errors.
   */
  public ListenableFuture<HTTPResponseData> requestAsync(final HTTPAddress ha, final HTTPRequest request, final ByteBuffer body) {
    return requestAsync(ha, request, body, TimeUnit.MILLISECONDS, defaultTimeout);
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
  public ListenableFuture<HTTPResponseData> requestAsync(final HTTPAddress ha, final HTTPRequest request, final ByteBuffer body, final TimeUnit unit, final long timeout) {
    HTTPRequestWrapper hrw = new HTTPRequestWrapper(request, ha, body, unit.toMillis(timeout));
    final ListenableFuture<HTTPResponseData> lf = hrw.slf;
    queue.add(hrw);
    if(ntse != null) {
      ntse.wakeup();
      if(isRunning.compareAndSet(false, true)) {
        ssi.execute(new RunSocket());
      }
    } else {
      processQueue();
    }
    return lf;
  }

  private void processQueue() {
    //This should be done after we do a .select on the ntse to check for more jobs before it exits.
    while(maxConcurrent > inProcess.size()  && !queue.isEmpty()) {
      HTTPRequestWrapper hrw = queue.poll();
      //This runs concurrently if using a threaded SocketExecuter, so we have to null check before we add.
      if(hrw != null) {
        try {
          hrw.client = getTCPClient(hrw.ha);
          inProcess.put(hrw.client, hrw);
          startWrite(hrw);
        } catch (Exception e) {
          //Have to catch all here or we dont keep processing if NoThreadSE is in use
          hrw.slf.setFailure(e);
        }
      }
    }
  }

  @Override
  protected void startupService() {
    // TODO Auto-generated method stub
    
  }

  @Override
  protected void shutdownService() {
    if(ntse != null) {
      ntse.stopIfRunning();
    }
    if(sts != null) {
      sts.shutdownNow();
    }
  }

  private TCPClient getTCPClient(final HTTPAddress ha) throws IOException {
    ArrayDeque<TCPClient> ll = sockets.get(ha);
    TCPClient tc = null;
    if(ll != null) {
      synchronized(ll) {
        while(ll.size() > 0 && tc == null) {
          if(ll.peek().isClosed()) {
            ll.pop();
          } else {
            tc = ll.pop();
          }
        }
        if(ll.size() == 0) {
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
    if(!client.isClosed()) {
      ArrayDeque<TCPClient> ll = sockets.get(ha);  
      if(ll == null) {
        sockets.put(ha, new ArrayDeque<TCPClient>());
        ll = sockets.get(ha);
      }
      synchronized(ll) {
        ll.add(client);
      }
    }
  }

  private void startWrite(HTTPRequestWrapper hrw) {
    //Request started here so we set the timeout to start now.
    hrw.updateReadTime();
    sei.watchFuture(hrw.slf, hrw.timeTillExpired()+1);
    hrw.client.write(hrw.hr.getByteBuffer());
    hrw.client.write(hrw.body.duplicate());
  }

  /**
   * Used to run the NoThreadSocketExecuter.
   */
  private class RunSocket implements Runnable {
    @Override
    public void run() {
      if(ntse.isRunning()) {
        ntse.select(100);
      }
      if(ntse.isRunning() && queue.size() + inProcess.size() > 0) {
        processQueue();
        ssi.execute(this);
      } else {
        isRunning.set(false);
      }
    }
  }

  /**
   * 
   * @author lwahlmeier
   *
   */
  private class MainClientProcessor implements Reader, CloseListener {

    @Override
    public void onClose(Client client) {
      HTTPRequestWrapper hrw = inProcess.get(client);
      if(hrw != null) {
        hrw.hrp.connectionClosed();
      }
      client.close();
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
   * @author lwahlmeier
   *
   */
  private class HTTPRequestWrapper implements HTTPResponseCallback {
    private final SettableListenableFuture<HTTPResponseData> slf = new SettableListenableFuture<HTTPResponseData>(false);
    private final HTTPResponseProcessor hrp = new HTTPResponseProcessor();
    private final HTTPRequest hr;
    private final HTTPAddress ha;
    private final long timeout;
    private final ByteBuffer body;
    private HTTPResponse response;
    private MergedByteBuffers responseMBB = new MergedByteBuffers();
    private TCPClient client;
    private long lastRead = Clock.lastKnownForwardProgressingMillis();

    public HTTPRequestWrapper(HTTPRequest hr, HTTPAddress ha, ByteBuffer body, long timeout) {
      hrp.addHTTPRequestCallback(this);
      this.hr = hr;
      this.ha = ha;
      this.body = body;
      this.timeout = timeout;
    }

    public void updateReadTime() {
      lastRead = Clock.lastKnownForwardProgressingMillis();
    }

    public long timeTillExpired() {
      return timeout - (Clock.lastKnownForwardProgressingMillis() - lastRead);
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
      slf.setResult(new HTTPResponseData(response, responseMBB.duplicateAndClean()));
      hrp.removeHTTPRequestCallback(this);
      inProcess.remove(client);
      addBackTCPClient(ha, client);
      processQueue();
    }

    @Override
    public void hasError(Throwable t) {
      slf.setFailure(t);
    }
  }

  /**
   * This is returned when a request finishes.  
   * 
   * @author lwahlmeier
   *
   */
  public static class HTTPResponseData {
    private final HTTPResponse hr;
    private final MergedByteBuffers body;

    public HTTPResponseData(HTTPResponse hr, MergedByteBuffers bb) {
      this.hr = hr;
      this.body = bb;
    }

    public HTTPResponse getResponse() {
      return hr;
    }

    public MergedByteBuffers getBody() {
      return body.copy();
    }

    public String getBodyAsString() {
      return body.copy().getAsString(body.remaining());
    }
  }

}
