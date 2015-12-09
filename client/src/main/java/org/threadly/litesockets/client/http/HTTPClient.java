package org.threadly.litesockets.client.http;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.threadly.util.Clock;

/**
 * <p>This is a HTTPClient for doing many simple HTTPRequests.  Every request will be make a new connection and requests
 * can be done in parallel.  This is mainly used for doing many smaller Request and Response messages as the full Request/Response 
 * is kept in memory and are not handled as streams.  See {@link HTTPStreamClient} for use with large HTTP data sets.</p>   
 * 
 */
public class HTTPClient {
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
  private final ConcurrentHashMap<HTTPAddress, LinkedList<TCPClient>> sockets = new ConcurrentHashMap<HTTPAddress, LinkedList<TCPClient>>();
  private final MainClientProcessor mcp = new MainClientProcessor();
  private final int maxConcurrent;
  private volatile int defaultTimeout = DEFAULT_TIMEOUT;

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
   */
  public HTTPClient(int maxConcurrent, int maxResponseSize, SocketExecuter sei) {
    this.maxConcurrent = maxConcurrent;
    this.maxResponseSize = maxResponseSize;
    this.ssi = sei.getThreadScheduler();
    this.sei = sei;
  }
  
  public HTTPResponseData request(URL url) throws HTTPParsingException {
    HTTPResponseData hr = null;
    try {
      hr = requestAsync(url).get();
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
  
  public void setTimeout(int timeout) {
    this.defaultTimeout = timeout;
  }
  
  public HTTPResponseData request(final HTTPAddress ha, final HTTPRequest request) throws HTTPParsingException{
    return request(ha, request, EMPTY_BUFFER);
  }
  
  public HTTPResponseData request(final HTTPAddress ha, final HTTPRequest request, final ByteBuffer body) throws HTTPParsingException {
    return request(ha, request, body, TimeUnit.MILLISECONDS, defaultTimeout);
  }
  
  public HTTPResponseData request(final HTTPAddress ha, final HTTPRequest request, final ByteBuffer body, final TimeUnit tu, final long time) 
      throws HTTPParsingException {
    HTTPResponseData hr = null;
    try {
      hr = requestAsync(ha, request, body, tu, time).get();
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
  
  public ListenableFuture<HTTPResponseData> requestAsync(final HTTPAddress ha, final HTTPRequest request) {
    return requestAsync(ha, request, EMPTY_BUFFER);
  }
  
  public ListenableFuture<HTTPResponseData> requestAsync(final HTTPAddress ha, final HTTPRequest request, final ByteBuffer body) {
    return requestAsync(ha, request, body, TimeUnit.MILLISECONDS, defaultTimeout);
  }
  
  public ListenableFuture<HTTPResponseData> requestAsync(final HTTPAddress ha, final HTTPRequest request, 
      final ByteBuffer body, final TimeUnit tu, final long time) {
    HTTPRequestWrapper hrw = new HTTPRequestWrapper(request, ha, body, tu.toMillis(time));
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
        } catch (IOException e) {
          hrw.slf.setFailure(e);
        }
      }
    }
  }

  /**
   * Stops the HttpClient.  Any requests currently pending will not be ran.  If this is not called
   * and HTTPClient looses all references it will be GCed correctly. 
   */
  public void stop() {
    if(ntse != null) {
      ntse.stopIfRunning();
      ntse.wakeup();
      ntse.wakeup();
    }
    if(sts != null) {
      sts.shutdownNow();
    }
  }
  
  private TCPClient getTCPClient(final HTTPAddress ha) throws IOException {
    LinkedList<TCPClient> ll = sockets.get(ha);
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
      if(ha.getdoSSL()) {
        SSLEngine sse = SSLUtils.OPEN_SSL_CTX.createSSLEngine(ha.getHost(), ha.getPort());
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
      LinkedList<TCPClient> ll = sockets.get(ha);  
      if(ll == null) {
        sockets.put(ha, new LinkedList<TCPClient>());
        ll = sockets.get(ha);
      }
      synchronized(ll) {
        ll.add(client);
      }
    }
  }
  
  private void startWrite(HTTPRequestWrapper hrw) {
    hrw.client.write(hrw.hr.getByteBuffer());
    hrw.client.write(hrw.body.duplicate());
    //Request started here so we set the timeout to start now.
    hrw.updateReadTime();
    sei.watchFuture(hrw.slf, hrw.timeTillExpired()+1);
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
