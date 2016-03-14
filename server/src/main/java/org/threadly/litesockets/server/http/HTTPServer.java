package org.threadly.litesockets.server.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.threadly.concurrent.event.ListenerHelper;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.Client.CloseListener;
import org.threadly.litesockets.Client.Reader;
import org.threadly.litesockets.Server.ClientAcceptor;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.TCPClient;
import org.threadly.litesockets.TCPServer;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestProcessor;
import org.threadly.litesockets.protocols.http.request.HTTPRequestProcessor.HTTPRequestCallback;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.utils.MergedByteBuffers;
import org.threadly.util.AbstractService;
import org.threadly.util.ExceptionUtils;

public class HTTPServer extends AbstractService {
  private static final Logger log = Logger.getLogger(HTTPServer.class.getSimpleName());
  
  private final ConcurrentHashMap<TCPClient, HTTPRequestProcessor> clients = new ConcurrentHashMap<TCPClient, HTTPRequestProcessor>();
  private final ClientListener clientListener = new ClientListener();
  private final ListenerHelper<Handler> handler = ListenerHelper.build(Handler.class);
  private final SSLContext sslc;
  private final SocketExecuter se;
  private final TCPServer server;
  private final String hostname;
  private final int port;
  
  public HTTPServer(SocketExecuter se, String hostName, int port, SSLContext sslc) throws IOException {
    this.se = se;
    this.hostname = hostName;
    this.port = port;
    this.sslc = sslc;
    this.server = this.se.createTCPServer(hostname, port);
    server.setClientAcceptor(clientListener);
    
    if(this.sslc != null) {
      server.setSSLContext(this.sslc);
      server.setSSLHostName(hostname);
      server.setDoHandshake(true);
    }
  }
  
  public int getListenPort() {
    return port;
  }
  
  public String getHostName() {
    return hostname;
  }

  @Override
  protected void startupService() {
    server.start();
  }

  @Override
  protected void shutdownService() {
    server.stop();
    server.close();
  }
  
  public void setHandler(Handler handler) {
    this.handler.addListener(handler);
  }
  
  private class ClientListener implements ClientAcceptor, Reader, CloseListener {

    @Override
    public void accept(Client client) {
      //log.info("New client connection:"+client);
      TCPClient tclient = (TCPClient)client;
      HTTPRequestProcessor hrp = new HTTPRequestProcessor();
      hrp.addHTTPRequestCallback(new HTTPRequestListener(tclient));
      clients.put(tclient, hrp);
      client.setReader(this);
      client.addCloseListener(this);
    }

    @Override
    public void onClose(Client client) {
      log.info("Client connection closed:"+client);
      clients.remove(client);
    }

    @Override
    public void onRead(Client client) {
      MergedByteBuffers mbb = client.getRead();
      //System.out.println(mbb.copy().getAsString(mbb.remaining())+":"+client);
      clients.get(client).processData(mbb);
    }
  }
  
  private class HTTPRequestListener implements HTTPRequestCallback {
    
    final TCPClient client;
    BodyFuture bodyFuture;
    ResponseWriter responseWriter;
    HTTPRequest hr = null; 
    
    HTTPRequestListener(TCPClient client) {
      this.client = client;
      bodyFuture = new BodyFuture();
      responseWriter = new ResponseWriter(this.client);
    }

    @Override
    public void headersFinished(HTTPRequest hr) {
      this.hr = hr;
      //log.info("From:\""+client.getRemoteSocketAddress()+"\": Method:\""+hr.getHTTPRequestHeaders().getRequestType()+"\" Path:\""+hr.getHTTPRequestHeaders().getRequestPath()+"\"");
      handler.call().handle(hr, bodyFuture, responseWriter);
    }

    @Override
    public void bodyData(ByteBuffer bb) {
      bodyFuture.addBody(hr, bb, responseWriter);
    }

    @Override
    public void finished() {
      bodyFuture.completed(hr, responseWriter);
      bodyFuture = new BodyFuture();
      responseWriter = new ResponseWriter(this.client);
    }

    @Override
    public void hasError(Throwable t) {
      ExceptionUtils.handleException(t);
    }
  }
  
  public static class ResponseWriter { 
    private final Client client;
    private boolean responseSent = false;
    private boolean done = false;
    private boolean closeOnDone = false;
    private ListenableFuture<?> lastWriteFuture;
    
    protected ResponseWriter(Client client) {
      this.client = client;
    }
    
    public void sendHTTPResponse(HTTPResponse hr) {
      if(!responseSent && ! done) {
        if(hr.getResponseHeader().getHTTPVersion().equals(HTTPConstants.HTTP_VERSION_1_0)) {
          closeOnDone = true;
        }
        responseSent = true;
        client.write(hr.getByteBuffer());
      } else if (responseSent) {
        throw new IllegalStateException("HTTPResponse already sent!");
      } else {
        throw new IllegalStateException("Cant write HTTPResponse, Response is already finished!");
      }
    }
    
    public void writeBody(ByteBuffer bb) {
      if(responseSent && !done) {
        lastWriteFuture = client.write(bb);
      } else if(responseSent){
        throw new IllegalStateException("Can not send body before HTTPResponse!");
      } else {
        throw new IllegalStateException("Cant write body, Response is already finished!");
      }
    }
    
    public void done() {
      done = true;
      if(closeOnDone && !client.isClosed()) {
        lastWriteFuture.addListener(new Runnable(){
          @Override
          public void run() {
            client.close();
          }});
      }
    }
    
    public void closeConnection() {
      done = true;
      client.close();
    }
    
    
  }
  
  public static class BodyFuture {
    private final ListenerHelper<BodyListener> listener = ListenerHelper.build(BodyListener.class);
    
    public void setBodyListener(BodyListener listener) {
      this.listener.clearListeners();
      this.listener.addListener(listener);
    }
    
    protected void addBody(HTTPRequest httpRequest, ByteBuffer bb, ResponseWriter responseWriter) {
      listener.call().onBody(httpRequest, bb, responseWriter);
    }
    
    protected void completed(HTTPRequest httpRequest, ResponseWriter responseWriter) {
      listener.call().bodyComplete(httpRequest, responseWriter);
    }
  }
  
  public interface Handler {
    HTTPServer getServer();
    void handle(HTTPRequest httpRequest, BodyFuture bodyListener, ResponseWriter responseWriter);
  }
  
  public interface BodyListener {
    public void onBody(HTTPRequest httpRequest, ByteBuffer bb, ResponseWriter responseWriter);
    public void bodyComplete(HTTPRequest httpRequest, ResponseWriter responseWriter);
  }

}
