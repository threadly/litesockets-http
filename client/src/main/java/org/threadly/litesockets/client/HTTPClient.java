package org.threadly.litesockets.protocol.http;

import java.util.LinkedList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLEngine;

import org.threadly.concurrent.SubmitterScheduler;
import org.threadly.concurrent.SingleThreadScheduler;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.Client.CloseListener;
import org.threadly.litesockets.Client.Reader;
import org.threadly.litesockets.NoThreadSocketExecuter;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.TCPClient;
import org.threadly.litesockets.utils.MergedByteBuffers;
import org.threadly.litesockets.utils.SSLUtils;
import org.threadly.protocols.http.request.HTTPRequest;
import org.threadly.protocols.http.response.HTTPResponse;
import org.threadly.protocols.http.response.HTTPResponseProcessor;
import org.threadly.protocols.http.shared.HTTPAddress;
import org.threadly.util.Clock;

/**
 * <p>This is a HTTPClient for doing many simple HTTPRequests.  Every request will be make a new connection and requests
 * can be done in parallel.  This is mainly used for doing many smaller Request and Response messages as the full Request/Response 
 * is kept in memory and are not handled as streams.  See {@link HTTPStreamClient} for use with large HTTP data sets.</p>   
 * 
 */
public class HTTPClient implements Reader, CloseListener {
  public static final int DEFAULT_CONCURRENT = 2; 
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

  /**
   * This does a blocking request returning the result.  The thread this is done on does not do any of the processing.
   * This ends up just calling requestAsync and doing a .get on the future.
   * 
   * @param request This is the request you want to make.  This is generally provided from calling .build on an HTTPRequestBuilder. 
   * @return Returns an HTTPResponse.  This is the response to your request with either the response message and body or the error that was recived.
   */
  public HTTPResponse request(HTTPRequest request) {
    HTTPResponse hr = null;
    try {
      hr = requestAsync(request).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      hr = new HTTPResponse(e.getCause());
    } catch (CancellationException e) {
      hr = new HTTPResponse(e);
    } 
    return hr;
  }

  /**
   * This does an Asynchronous HTTPRequest.  
   * 
   * @param request The request to make Asynchronously.
   * @return A ListenableFuture is returned here.  It will either have an error or the HTTPResponse.
   */
  public ListenableFuture<HTTPResponse> requestAsync(final HTTPRequest request) {
    HTTPRequestWrapper hrw = new HTTPRequestWrapper(request);
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
        LinkedList<TCPClient> ll = sockets.get(hrw.hr.getHTTPAddress());
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
        LinkedList<TCPClient> ll = sockets.get(hrw.hr.getHTTPAddress());
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
          hrw.client = sei.createTCPClient(hrw.hr.getHost(), hrw.hr.getPort());
          if(hrw.hr.doSSL()) {
            SSLEngine sse = SSLUtils.OPEN_SSL_CTX.createSSLEngine(hrw.hr.getHost(), hrw.hr.getPort());
            sse.setUseClientMode(true);
            hrw.client.setSSLEngine(sse);
            hrw.client.startSSL();
          }
          hrw.client.setReader(HTTPClient.this);
          hrw.client.addCloseListener(HTTPClient.this);
          sockets.putIfAbsent(hrw.hr.getHTTPAddress(), new LinkedList<TCPClient>());
        }
        hrw.client.connect();
        hrw.client.write(hrw.hr.getRequestBuffer());
        
        //Request started here so we set the timeout to start now.
        hrw.updateReadTime();
        sei.watchFuture(hrw.slf, hrw.timeTillExpired()+1);
        if(hrw.isExpired()) {
          hrw.slf.setFailure(new TimeoutException());
        }
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  private class CheckTimeout implements Runnable {
    private final HTTPRequestWrapper hrw;
    
    public CheckTimeout(HTTPRequestWrapper hrw) {
      this.hrw = hrw;
    }

    @Override
    public void run() {
      if(hrw.slf.isDone()) {
        return;
      }
      if(hrw.isExpired()) {
        hrw.slf.setResult(new HTTPResponse(new TimeoutException("Request timedout")));
      } else {
        ssi.schedule(this, hrw.timeTillExpired()+1);
      }
    }
  }
  
  private static class HTTPRequestWrapper {
    final HTTPRequest hr;
    final SettableListenableFuture<HTTPResponse> slf = new SettableListenableFuture<HTTPResponse>();
    final HTTPResponseProcessor hrp = new HTTPResponseProcessor();
    volatile TCPClient client;
    volatile long lastRead = Clock.lastKnownForwardProgressingMillis();
    
    public HTTPRequestWrapper(HTTPRequest hr) {
      this.hr = hr;
    }
    
    public void updateReadTime() {
      lastRead = Clock.lastKnownForwardProgressingMillis();
    }
    
    public int timeTillExpired() {
      return hr.getTimeout() - (int) (Clock.lastKnownForwardProgressingMillis() - lastRead);
    }
    
    public boolean isExpired() {
      return hr.getTimeout() <= (int) (Clock.lastKnownForwardProgressingMillis() - lastRead);
    }
  }


}
