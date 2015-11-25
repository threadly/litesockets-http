package org.threadly.litesockets.client.http;

import java.net.URL;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
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
import org.threadly.litesockets.protocols.http.shared.HTTPAddress;
import org.threadly.litesockets.utils.MergedByteBuffers;
import org.threadly.litesockets.utils.SSLUtils;
import org.threadly.util.Clock;

/**
 * <p>This is a HTTPClient for doing many simple HTTPRequests.  Every request will be make a new connection and requests
 * can be done in parallel.  This is mainly used for doing many smaller Request and Response messages as the full Request/Response 
 * is kept in memory and are not handled as streams.  See {@link HTTPStreamClient} for use with large HTTP data sets.</p>   
 * 
 */
public class HTTPClient implements Reader, CloseListener {
  public static final int DEFAULT_CONCURRENT = 2;
  public static final int DEFAULT_TIMEOUT = 15000;
  public static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0); 
  public static final int MAX_HTTP_RESPONSE = 1048576;  //1MB
  
  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final int maxResponseSize;
  private final SubmitterScheduler ssi;
  private final SocketExecuter sei;
  private final ConcurrentLinkedQueue<HTTPRequestWrapper> queue = new ConcurrentLinkedQueue<HTTPRequestWrapper>();
  private final ConcurrentLinkedQueue<HTTPRequestWrapper> inProcess = new ConcurrentLinkedQueue<HTTPRequestWrapper>();
  private final ConcurrentHashMap<HTTPAddress, LinkedList<TCPClient>> sockets = new ConcurrentHashMap<HTTPAddress, LinkedList<TCPClient>>();
  
  private final int maxConcurrent;

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
   * as well as your own {@link SimpleSchedulerInterface} as the thread pool to use.</p> 
   * 
   */
  public HTTPClient(int maxConcurrent, int maxResponseSize, SocketExecuter sei) {
    this.maxConcurrent = maxConcurrent;
    this.maxResponseSize = maxResponseSize;
    this.ssi = sei.getThreadScheduler();
    this.sei = sei;
  }
  
  public HTTPResponse request(URL url) {
    boolean ssl = false;
    int port = 80;
    String host = url.getHost();
    if(url.getProtocol().equalsIgnoreCase("https")) {
      port = 443;
      ssl = true;
    }
    if(url.getPort() != -1) {
      port = url.getPort();
    }
    return request(new HTTPAddress(host, port, ssl), new HTTPRequestBuilder(url).build());
  }
  
  public HTTPResponse request(final HTTPAddress ha, final HTTPRequest request) {
    return request(ha, request, EMPTY_BUFFER);
  }
  public HTTPResponse request(final HTTPAddress ha, final HTTPRequest request, final ByteBuffer body) {
    return request(ha, request, body, TimeUnit.MILLISECONDS, DEFAULT_TIMEOUT);
  }
  public HTTPResponse request(final HTTPAddress ha, final HTTPRequest request, final ByteBuffer body, final TimeUnit tu, final long time) {
    HTTPResponse hr = null;
    try {
      hr = requestAsync(ha, request, body, tu, time).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      hr = new HTTPResponse(e.getCause());
    } catch (CancellationException e) {
      hr = new HTTPResponse(e);
    }
    return hr;
  }

  public ListenableFuture<HTTPResponse> requestAsync(URL url) {
    boolean ssl = false;
    int port = 80;
    String host = url.getHost();
    if(url.getProtocol().equalsIgnoreCase("https")) {
      port = 443;
      ssl = true;
    }
    if(url.getPort() != -1) {
      port = url.getPort();
    }
    return requestAsync(new HTTPAddress(host, port, ssl), new HTTPRequestBuilder(url).build());
  }
  
  public ListenableFuture<HTTPResponse> requestAsync(final HTTPAddress ha, final HTTPRequest request) {
    return requestAsync(ha, request, EMPTY_BUFFER);
  }
  
  public ListenableFuture<HTTPResponse> requestAsync(final HTTPAddress ha, final HTTPRequest request, final ByteBuffer body) {
    return requestAsync(ha, request, body, TimeUnit.MILLISECONDS, DEFAULT_TIMEOUT);
  }
  
  public ListenableFuture<HTTPResponse> requestAsync(final HTTPAddress ha, final HTTPRequest request, final ByteBuffer body, final TimeUnit tu, final long time) {
    HTTPRequestWrapper hrw = new HTTPRequestWrapper(request, ha, body, tu.toMillis(time));
    final ListenableFuture<HTTPResponse> lf = hrw.slf;
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
        inProcess.add(hrw);
        ssi.execute(new RunClient(hrw));
      }
      
    }
  }
  
  @Override
  public void onClose(Client client) {
    HTTPRequestWrapper hrw = null;
    for(HTTPRequestWrapper tmp: inProcess) {
      if(tmp.client == client) {
        hrw = tmp;
        break;
      }
    }
    if(hrw != null) {
      inProcess.remove(hrw);
      if(!hrw.slf.isDone()) {
        hrw.hrp.doClose();
        if(hrw.hrp.isDone()) {
          hrw.slf.setResult(hrw.hrp.getResponse());
        }else {
          hrw.slf.setResult(new HTTPResponse(new Exception("Error Closed before Response was Done!")));
        }
      }
    }
  }

  @Override
  public void onRead(Client client) {
    TCPClient tc = null;
    HTTPRequestWrapper hrw = null;
    for(HTTPRequestWrapper tmp: inProcess) {
      if(tmp.client == client) {
        tc = (TCPClient)client;
        hrw = tmp;
        break;
      }
    }
    if(hrw != null && !hrw.hrp.isDone() && !hrw.hrp.isError()) {
      hrw.updateReadTime();
      MergedByteBuffers mbb = client.getRead();
      hrw.hrp.process(mbb);
      if(hrw.hrp.isDone() && !hrw.hrp.isError()) {
        hrw.slf.setResult(hrw.hrp.getResponse());
        LinkedList<TCPClient> ll = sockets.get(hrw.ha);
        synchronized(ll) {
          if(!ll.contains(hrw.client)) {
            ll.add(hrw.client);
          }
        }
        inProcess.remove(hrw);
        processQueue();
      } else if (hrw.hrp.isError()) {
        hrw.slf.setResult(new HTTPResponse(new Exception("Error while parsing HTTP: "+hrw.hrp.getErrorText())));
        tc.close();
        inProcess.remove(hrw);
        processQueue();
      } else if (hrw.hrp.getBodyLength() > maxResponseSize) {
        hrw.slf.setResult(new HTTPResponse(new Exception("HTTPResponse was to large!! content-length was set to :"+hrw.hrp.getBodyLength())));
        tc.close();
        inProcess.remove(hrw);
        processQueue();
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
   * Used when an HTTPClient is ready to start processing.
   *
   */
  private class RunClient implements Runnable {
    final HTTPRequestWrapper hrw;
    
    public RunClient(HTTPRequestWrapper hrw) {
      this.hrw = hrw;
    }
    
    @Override
    public void run() {
      try {
        LinkedList<TCPClient> ll = sockets.get(hrw.ha);
        if(ll != null) {
          synchronized(ll) {
            while(ll.size() > 0 && hrw.client == null) {
              if(ll.peek().isClosed()) {
                ll.pop();
              } else {
                hrw.client = ll.pop();
              }
            }
          }
        }
        if(hrw.client == null || hrw.client.isClosed()) {
          hrw.client = sei.createTCPClient(hrw.ha.getHost(), hrw.ha.getPort());
          hrw.client.setConnectionTimeout((int)hrw.timeout);
          if(hrw.ha.getdoSSL()) {
            SSLEngine sse = SSLUtils.OPEN_SSL_CTX.createSSLEngine(hrw.ha.getHost(), hrw.ha.getPort());
            sse.setUseClientMode(true);
            hrw.client.setSSLEngine(sse);
            hrw.client.startSSL();
          }
          hrw.client.setReader(HTTPClient.this);
          hrw.client.addCloseListener(HTTPClient.this);
          sockets.putIfAbsent(hrw.ha, new LinkedList<TCPClient>());
        }
        hrw.client.connect();
        hrw.client.write(hrw.hr.getCombinedBuffers());
        hrw.client.write(hrw.body.duplicate());
        //Request started here so we set the timeout to start now.
        hrw.updateReadTime();
        sei.watchFuture(hrw.slf, hrw.timeTillExpired()+1);
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  private static class HTTPRequestWrapper {
    final HTTPRequest hr;
    final HTTPAddress ha;
    final ByteBuffer body;
    final SettableListenableFuture<HTTPResponse> slf = new SettableListenableFuture<HTTPResponse>();
    final HTTPResponseProcessor hrp = new HTTPResponseProcessor();
    volatile TCPClient client;
    volatile long lastRead = Clock.lastKnownForwardProgressingMillis();
    final long timeout;
    
    public HTTPRequestWrapper(HTTPRequest hr, HTTPAddress ha, ByteBuffer body, long timeout) {
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
  }


}
