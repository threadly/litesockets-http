package org.threadly.litesockets.client.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLEngine;

import org.threadly.concurrent.event.RunnableListenerHelper;
import org.threadly.concurrent.future.FutureCallback;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.Client.ClientCloseListener;
import org.threadly.litesockets.Client.Reader;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.TCPClient;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseProcessor;
import org.threadly.litesockets.protocols.http.response.HTTPResponseProcessor.HTTPResponseCallback;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;
import org.threadly.litesockets.protocols.websocket.WSFrame;
import org.threadly.litesockets.utils.SSLUtils;

/**
 * <p>HTTPStreamClient is designed to work with larger HTTPStreams of data.  This can mean sending them
 * or receiving them.  The basic concept is that once the connection is established and the HTTP headers 
 * are send and/or received everything pretty much works like a normal socket connection in litesockets.
 * </p>
 * 
 * <p>The HttpStreamClient can also do many requests/responses in a row w/o closing the connection</p>
 * 
 * <p>Its important to note that if chunked encoding is set in a header anything sent and/or received will 
 * automatically remove the chunk headers.  If the HTTPResponse is chunked you will get a call to onRead for 
 * each chunk but the chunk header will be removed.  If you are sending and have turned on chunked encoding
 * every call to .write will add a chunked header to it, and calling .write with a ByteBuffer of 0 will end the 
 * stream</p>
 * 
 * 
 * @author lwahlmeier
 *
 */
public class HTTPStreamClient implements StreamingClient {
  private static final int DEFAULT_TIMEOUT = 20000;
  private final Reader classReader = new HTTPReader();
  private final ClientCloseListener classCloser = new HTTPCloser();
  private final RunnableListenerHelper closeListener = new RunnableListenerHelper(true);
  private final RequestCallback requestCB = new RequestCallback();
  private final TCPClient client;
  private final String host;
  private final int port;
  
  private final HTTPResponseProcessor httpProcessor;
  
  private volatile boolean isConnected = false;
  private volatile HTTPStreamReader httpReader;
  private volatile SettableListenableFuture<HTTPResponse> slfResponse;
  private volatile HTTPRequest currentHttpRequest;
  
  /**
   * Creates an HTTPStreaming client from an already existing TCPClient.
   * 
   * @param client the {@link TCPClient} to use for this connection.
   */
  public HTTPStreamClient(TCPClient client) {
    this(client, client.getRemoteSocketAddress().getHostName());
  }

  /**
   * Creates an HTTPStreaming client from an already existing TCPClient.
   * 
   * @param client the {@link TCPClient} to use for this connection.
   * @param host the hostname or ip address to connect to.
   */
  public HTTPStreamClient(TCPClient client, String host) {
    this.client = client;
    this.host = host;
    port = client.getRemoteSocketAddress().getPort();
    client.addCloseListener(classCloser);
    httpProcessor = new HTTPResponseProcessor(false);
    httpProcessor.addHTTPResponseCallback(requestCB);
    slfResponse = new SettableListenableFuture<HTTPResponse>();
    isConnected = true;
  }

  /**
   * <p>When constructing the HTTPStreamClient we connect to a specific server.  If you 
   * want to use the HTTPRequest to set the server/port you can use the .getHost() and
   * .getPort() methods on it.</p> 
   *
   * @param se the SocketExecuter to use for this client.
   * @param host the hostname or ip address to connect to.
   * @param port the tcp port to connect to.
   * @throws IOException this will happen if we have problems connecting for some reason.
   */
  public HTTPStreamClient(SocketExecuter se, String host, int port) throws IOException {
    this.host = host;
    this.port = port;
    client = se.createTCPClient(host, port);
    client.setConnectionTimeout(DEFAULT_TIMEOUT);
    client.addCloseListener(classCloser);
    httpProcessor = new HTTPResponseProcessor(false);
    httpProcessor.addHTTPResponseCallback(requestCB);
  }
  
  @Override
  public void enableSSL() {
    SSLEngine ssle = SSLUtils.OPEN_SSL_CTX.createSSLEngine(host, port);
    ssle.setUseClientMode(true);
    enableSSL(ssle);
  }
  
  @Override
  public void enableSSL(SSLEngine ssle) {
    ssle.setUseClientMode(true);
    client.setSSLEngine(ssle);
    client.startSSL();
  }
  
  @Override
  public void setConnectionTimeout(int timeout) {
    client.setConnectionTimeout(timeout);
  }
  
  /**
   * 
   * @return the connected Host name/ip.
   */
  public String getHost() {
    return host;
  }
  
  /**
   * 
   * @return Returns the connected port number.
   */
  public int getPort() {
    return port;
  }

  @Override
  public void setRequestResponseHeaders(HTTPRequest httpRequest, HTTPResponse httpResponse, boolean writeResponse) {
    if(!slfResponse.isDone()) {
      currentHttpRequest = httpRequest;
      httpProcessor.processData(httpResponse.getByteBuffer());
      if(writeResponse) {
        client.write(httpResponse.getByteBuffer());
      }
    }
  }

  /**
   * <p>Tell the client to write an HTTPRequest to the server.  This can technically be done
   * whenever you want to but obviously use only when you know you can sent a request, right after
   * opening a connection for example.</p>
   * 
   * @param request the request to send.  This is generally done with HTTPRequestBuilders buildHeadersOnly() method.  
   * You can send a full request if you expect the response (not the sending) to be a stream.
   * @return returns a ListenableFuture with the HTTPResponse in it.  This might not callback right away, 
   * especially if you used buildHeadersOnly() as you still need to stream in your data till its complete.  
   * Once complete this should be called.
   */
  public ListenableFuture<HTTPResponse> writeRequest(HTTPRequest request) {
    if(slfResponse != null && !slfResponse.isDone()) {
      slfResponse.setFailure(new IllegalStateException("New request came in!"));
    }
    currentHttpRequest = request;
    slfResponse = new SettableListenableFuture<HTTPResponse>(false);
    client.write(request.getByteBuffer());
    return slfResponse;
  }
  
  @Override
  public ListenableFuture<?> write(ByteBuffer bb) {
    if(currentHttpRequest == null) {
      throw new IllegalStateException("Must have a pending HTTPRequest before you can write!");
    }
    if(currentHttpRequest != null && currentHttpRequest.getHTTPHeaders().isChunked()) {
      return client.write(HTTPUtils.wrapInChunk(bb));
    } else {
      return client.write(bb);
    }
  }
  
  /**
   * Get the {@link ListenableFuture} associated with the last write to the associated client.  
   * This future will complete when the write has been written to the socket.
   */
  public ListenableFuture<?> getLastWriteFuture() {
    return client.lastWriteFuture();
  }
  
  /**
   * Sets the HTTPStreamReader for this client.
   * 
   * @param hsr the {@link HTTPStreamReader}
   */
  public void setHTTPStreamReader(HTTPStreamReader hsr) {
    httpReader = hsr;
    if(hsr == null) {
      client.setReader(null);
    } else {
      client.setReader(classReader);
    }
  }

  @Override
  public Executor getClientsThreadExecutor() {
    return client.getClientsThreadExecutor();
  }

  public ListenableFuture<Boolean> connect() {
    ListenableFuture<Boolean> lf = client.connect();
    lf.callback(new FutureCallback<Boolean>(){
      @Override
      public void handleResult(Boolean result) {
        isConnected = true;
      }

      @Override
      public void handleFailure(Throwable t) {
        isConnected = false;
      }});
    return lf;
  }
  
  public void addCloseListener(Runnable cl) {
    if(!client.isClosed()) {
      closeListener.addListener(cl);
    } else {
      cl.run();
    }
  }
  
  /**
   * Check if the client is connected.
   * 
   * @return {@code true} if the client has not been closed yet
   */
  public boolean isConnected() {
    return isConnected;
  }

  /**
   * Close the associated client.
   */
  public void close() {
    isConnected = false;
    client.close();
  }  

  /**
   * Implementation of {@link Reader} which will provide the data to the {@code httpProcessor}.
   */
  private class HTTPReader implements Reader {
    @Override
    public void onRead(Client client) {
      httpProcessor.processData(client.getRead());
    }
  }

  /**
   * Implementation of {@link ClientCloseListener} to communicate the closed status to this class 
   * and attached listeners.
   */
  private class HTTPCloser implements ClientCloseListener {
    @Override
    public void onClose(Client client) {
      isConnected = false;
      closeListener.callListeners();
    }
  }
  
  /**
   * 
   * @author lwahlmeier
   *
   */
  private class RequestCallback implements HTTPResponseCallback {
    @Override
    public void headersFinished(HTTPResponse hr) {
      slfResponse.setResult(hr);
    }

    @Override
    public void bodyData(ByteBuffer bb) {
      if(httpReader != null) {
        httpReader.handle(bb);
      }
    }

    @Override
    public void finished() {
      close();
    }

    @Override
    public void hasError(Throwable t) {
      slfResponse.setFailure(t);
      client.close();
    }

    @Override
    public void websocketData(WSFrame wsf, ByteBuffer bb) {
      if(httpReader != null) {
        httpReader.handle(bb);
      }
    }
  }
  
  /**
   * Simple HTTPStreamReader callback interface.
   * 
   * @author lwahlmeier
   *
   */
  public interface HTTPStreamReader {
    /**
     * This is called when body reads come in from the client.
     * 
     * @param bb a {@link ByteBuffer} containing the next body data.
     */
    public void handle(ByteBuffer bb);
  }
}
