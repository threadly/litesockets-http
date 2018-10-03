package org.threadly.litesockets.server.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;

import org.threadly.concurrent.event.ListenerHelper;
import org.threadly.concurrent.event.RunnableListenerHelper;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.Client.ClientCloseListener;
import org.threadly.litesockets.Client.Reader;
import org.threadly.litesockets.Server.ClientAcceptor;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.TCPClient;
import org.threadly.litesockets.TCPServer;
import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.buffers.SimpleMergedByteBuffers;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestProcessor;
import org.threadly.litesockets.protocols.http.request.HTTPRequestProcessor.HTTPRequestCallback;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.litesockets.protocols.ws.WSFrame;
import org.threadly.litesockets.protocols.ws.WSOPCode;
import org.threadly.util.AbstractService;
import org.threadly.util.ExceptionUtils;

/**
 * A simple HTTPServer abstraction.
 * 
 * @author lwahlmeier
 *
 */
public class HTTPServer extends AbstractService {
  public static final HTTPResponse NOT_FOUND_RESPONSE = new HTTPResponseBuilder().setResponseCode(HTTPResponseCode.NotFound).build();
  
  private final ConcurrentHashMap<TCPClient, HTTPRequestProcessor> clients = new ConcurrentHashMap<>();
  private final ClientListener clientListener = new ClientListener();
  private final SocketExecuter se;
  private final TCPServer server;
  private final String hostname;
  private final int port;

  private volatile SSLContext sslc;
  private volatile HTTPServerHandler handler;
  
  /**
   * Constructs an {@link HTTPServer} without SSL support.
   * 
   * @param se The {@link SocketExecuter} to use for this HTTPServer.
   * @param hostName The hostname or ip to bind this httpServer too.
   * @param port the port this server will bind to.
   * @throws IOException this is thrown if we have problems creating this HTTPServers listen socket.
   */
  public HTTPServer(final SocketExecuter se, final String hostName, final int port) throws IOException {
    this(se, hostName, port, null);
  }
  
  /**
   * Constructs an {@link HTTPServer} with SSL support.
   * 
   * @param se The {@link SocketExecuter} to use for this HTTPServer.
   * @param hostName The hostname or ip to bind this httpServer too.
   * @param port the port this server will bind to.
   * @param sslc the {@link SSLContext} to use for this server.
   * @throws IOException this is thrown if we have problems creating this HTTPServers listen socket.
   */
  public HTTPServer(final SocketExecuter se, final String hostName, final int port, final SSLContext sslc) throws IOException {
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
  
  /**
   * @return the listen port this server is bound to.
   */
  public int getListenPort() {
    return port;
  }
  
  /**
   * Allows you to set/reset the ssl context on the server.  Good for dynamically reloading certs.
   * 
   * @param sslc The sslContext to use.
   */
  public void setSSLContext(final SSLContext sslc) {
    this.sslc = sslc;
    if(this.sslc != null) {
      server.setSSLContext(this.sslc);
      server.setSSLHostName(hostname);
      server.setDoHandshake(true);
    } else {
      server.setDoHandshake(false);
      server.setSSLContext(null);
    }
  }
  
  public void setExceptionHandler() {
    
  }
  
  /**
   * 
   * @return the ip/hostname this server is bound to.
   */
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
  
  /**
   * Sets an {@link HTTPServerHandler} to this server.
   * 
   * @param handler the handler to use.
   */
  public void addHandler(final HTTPServerHandler handler) {
    this.handler = handler;
  }
  
  /**
   * 
   * @author lwahlmeier
   *
   */
  private class ClientListener implements ClientAcceptor, Reader, ClientCloseListener {
    @Override
    public void accept(Client client) {
      TCPClient tclient = (TCPClient)client;
      if(handler.onConnection(tclient.getRemoteSocketAddress())) {
        HTTPRequestProcessor hrp = new HTTPRequestProcessor();
        hrp.addHTTPRequestCallback(new HTTPRequestListener(tclient));
        clients.put(tclient, hrp);
        client.setReader(this);
        client.addCloseListener(this);
      } else {
        tclient.close();
      }
    }

    @Override
    public void onClose(Client client) {
      handler.onDisconnect((InetSocketAddress)client.getRemoteSocketAddress(), client.getStats().getTotalRead(), client.getStats().getTotalWrite());
      HTTPRequestProcessor hrp = clients.remove(client);
      if(hrp != null) {
        hrp.connectionClosed();
      }
    }

    @Override
    public void onRead(Client client) {
      HTTPRequestProcessor hrp = clients.get(client);
      if(hrp != null) {
        hrp.processData(client.getRead());
      }
    }
  }
  
  /**
   * Listener for handling the incoming client request data and state.
   */
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
      if(handler != null) {
        handler.handle(hr, responseWriter, bodyFuture);
      } else {
        responseWriter.sendHTTPResponse(NOT_FOUND_RESPONSE);
        responseWriter.closeOnDone();
        responseWriter.done();
      }
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
      bodyFuture.onError(hr, responseWriter, t);
      bodyFuture.completed(hr, responseWriter);
      bodyFuture = new BodyFuture();
      responseWriter = new ResponseWriter(this.client);
    }

    @Override
    public void websocketData(WSFrame wsf, ByteBuffer bb) {
      bodyFuture.onWebsocketFrame(hr, wsf, bb, responseWriter);
    }
  }
  
  /**
   * This class is used to write responses to HTTPRequests that are made against the HTTPServer.
   * 
   * @author lwahlmeier
   */
  public static class ResponseWriter {
    private final Client client;
    private final RunnableListenerHelper closeListener = new RunnableListenerHelper(false);
    private boolean responseSent = false;
    private boolean done = false;
    private boolean closeOnDone = false;
    
    protected ResponseWriter(Client client) {
      this.client = client;
      this.client.addCloseListener(new ClientCloseListener() {
        @Override
        public void onClose(Client client) {
          closeListener.callListeners();
        }});
    }
    
    /**
     * Inform if the client has data pending to be written to the socket.  This includes header data. 
     * 
     * @return the size of data pending to be written to the socket.
     */
    public int pendingDataSize() {
      return client.getWriteBufferSize();
    }
    
    /**
     * This sends an {@link HTTPResponse} to the client.  This must be sent before any body data can be written.
     * 
     * @param hr the {@link HTTPResponse} to write to the client.
     * @return a {@link ListenableFuture} that will be complete once this data is written to the socket.
     */
    public ListenableFuture<?> sendHTTPResponse(HTTPResponse hr) {
      if(!responseSent && ! done) {
        if(hr.getResponseHeader().getHTTPVersion().equals(HTTPConstants.HTTP_VERSION_1_0)) {
          closeOnDone = true;
        }
        responseSent = true;
        return client.write(hr.getByteBuffer());
      } else if (responseSent) {
        throw new IllegalStateException("HTTPResponse already sent!");
      } else {
        throw new IllegalStateException("Cant write HTTPResponse, Response is already finished!");
      }
    }
    
    /**
     * This will force the connection to be closed once done is called and all pending data from that point has been written.
     * 
     */
    public void closeOnDone() {
      this.closeOnDone = true;
    }
    
    /**
     * informs if the clients connection is still open or not.
     * 
     * @return true if the client connection is closed, false if its still open.
     */
    public boolean isClosed() {
      return client.isClosed();
    }
    
    /**
     * Allows you to set a runnable to run once the clients connection is closed.
     * 
     * @param cl the Runnable to run once the connection is closed.
     */
    public void addCloseListener(Runnable cl) {
      closeListener.addListener(cl);
    }
    
    /**
     * Write body data to the client.  This can only be done after {@link #sendHTTPResponse(HTTPResponse)} has been called. 
     * You must have already setup what is being sent (Content-Length, chunked, etc) in the HTTPResponse call.
     * 
     * @param bb the data to write as the body for this client.
     * @return a {@link ListenableFuture} that will be complete once this data is written to the socket.
     */
    public ListenableFuture<?> writeBody(ByteBuffer bb) {
      if(responseSent && !done) {
        return client.write(bb);
      } else if(responseSent){
        throw new IllegalStateException("Can not send body before HTTPResponse!");
      } else {
        throw new IllegalStateException("Cant write body, Response is already finished!");
      }
    }

    /**
     * Write body data to the client.  This can only be done after {@link #sendHTTPResponse(HTTPResponse)} has been called. 
     * You must have already setup what is being sent (Content-Length, chunked, etc) in the HTTPResponse call.
     * 
     * @param mbb the data to write as the body for this client.
     * @return a {@link ListenableFuture} that will be complete once this data is written to the socket.
     */
    public ListenableFuture<?> writeBody(MergedByteBuffers mbb) {
      if(responseSent && !done) {
        return client.write(mbb);
      } else if(responseSent){
        throw new IllegalStateException("Can not send body before HTTPResponse!");
      } else {
        throw new IllegalStateException("Cant write body, Response is already finished!");
      }
    }
    
    public ListenableFuture<?> writeWebsocketFrame(WSOPCode wsoc, MergedByteBuffers mbb, boolean mask) {
      ByteBuffer bb = mbb.pullBuffer(mbb.remaining());
      return writeBody(new SimpleMergedByteBuffers(false, WSFrame.makeWSFrame(mbb.remaining(), wsoc.getValue(), mask).getRawFrame(), bb));
    }
    
    /**
     * This is called once you are done handling this HTTPRequest.  If the connection is not closed
     * the client can send a new HTTPRequest that will call back on the {@link HTTPServerHandler} again.
     */
    public void done() {
      done = true;
      if(closeOnDone && !client.isClosed()) {
        client.lastWriteFuture().addListener(new Runnable(){
          @Override
          public void run() {
            client.close();
          }});
      }
    }
    
    /**
     * forces this clients connection closed.
     */
    public void closeConnection() {
      done = true;
      client.close();
    }
    
    public InetSocketAddress getRemoteSocketAddress() {
      return (InetSocketAddress)client.getRemoteSocketAddress();
    }
    
    public InetSocketAddress getLocalSocketAddress() {
      return (InetSocketAddress)client.getLocalSocketAddress();
    }
  }
  
  /**
   * A simple callback class to allow HTTPServerHandlers to listen for body data as it 
   * comes in from the clients socket.
   * 
   * @author lwahlmeier
   *
   */
  public static class BodyFuture {
    private final ListenerHelper<BodyListener> listener = new ListenerHelper<>(BodyListener.class);
    
    /**
     * Sets the BodyListener to be used/called back on.
     * 
     * @param listener the listener to set.
     */
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
    
    protected void onError(HTTPRequest httpRequest, ResponseWriter responseWriter, Throwable t) {
      listener.call().onError(httpRequest, responseWriter, t);
    }
    
    protected void onWebsocketFrame(HTTPRequest httpRequest, WSFrame wsf, ByteBuffer bb, ResponseWriter responseWriter) {
      listener.call().onWebsocketFrame(httpRequest, wsf, bb, responseWriter);
    }
  }
  
  /**
   *  The servers handler interface.  This must be set to handle clients sending request to the server. 
   * 
   * @author lwahlmeier
   */
  public interface HTTPServerHandler {
    /**
     * This is called when a new HTTPRequest has came in on a client connection.
     * 
     * @param httpRequest the {@link HTTPRequest} the client sent.
     * @param responseWriter the {@link ResponseWriter} that is used to send responses back on.
     * @param bodyListener the {@link BodyFuture} that will be used to call back on as body data is read from the client.
     */
    void handle(HTTPRequest httpRequest, ResponseWriter responseWriter, BodyFuture bodyListener);
    
    /**
     * Allow handling of client connection before any HTTPData comes in or is parsed.
     * 
     * This allows you to block IPs or do logging if needed before parsing occurs.
     * 
     * @param isa the InetSocketAddress of the client connecting.
     * @return true to allow the connection to continue, false to immediately close the connection. (Defaults to true).
     */
    default boolean onConnection(InetSocketAddress isa) {
      return true;
    }
    
    /**
     * Allows you to know when a client disconnects.  Gives read/write stats for the clients raw socket.
     * 
     * @param isa the SocketAddress for the client
     * @param bytesRead bytes read from the client while it was connected.
     * @param bytesWritten bytes written to the client while it was connected.
     */
    default void onDisconnect(InetSocketAddress isa, long bytesRead, long bytesWritten) {

    }
  }
  
  /**
   * A simple callback interface used to receive body data from an HTTP client.
   * 
   * @author lwahlmeier
   */
  public interface BodyListener {
    /**
     * This is called as body data come in.  This is called in an in-order thread safe way.
     * 
     * @param httpRequest the initial {@link HTTPRequest} this body is for.
     * @param bb the current body data.
     * @param responseWriter the {@link ResponseWriter} for this client.
     */
    public void onBody(HTTPRequest httpRequest, ByteBuffer bb, ResponseWriter responseWriter);
    
    public void onWebsocketFrame(HTTPRequest httpRequest, WSFrame wsf, ByteBuffer bb, ResponseWriter responseWriter);
    
    /**
     * This is called when the body has completed.
     * NOTE: it is not always possible to know if the body data is done depending on the {@link HTTPRequest} so this might not get called.
     * 
     * @param httpRequest the initial {@link HTTPRequest} for this client.
     * @param responseWriter the {@link ResponseWriter} for this client.
     */
    public void bodyComplete(HTTPRequest httpRequest, ResponseWriter responseWriter);
    public default void onError(HTTPRequest httpRequest, ResponseWriter responseWriter, Throwable t) {
      ExceptionUtils.handleException(t);
    }
  }
}
