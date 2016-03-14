package org.threadly.litesockets.client.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import org.threadly.concurrent.event.ListenerHelper;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.Client.CloseListener;
import org.threadly.litesockets.Client.Reader;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.TCPClient;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseProcessor;
import org.threadly.litesockets.protocols.http.response.HTTPResponseProcessor.HTTPResponseCallback;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;
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
public class HTTPStreamClient {
  private final Reader classReader = new HTTPReader();
  private final CloseListener classCloser = new HTTPCloser();
  private final TCPClient client;
  private final RequestCallback requestCB = new RequestCallback();
  
  private final HTTPResponseProcessor httpProcessor;
  private final ListenerHelper<HTTPStreamReader> httpReader = ListenerHelper.build(HTTPStreamReader.class);
  private volatile SettableListenableFuture<HTTPResponse> slfResponse;
  private volatile HTTPRequest currentHttpRequest;

  public HTTPStreamClient(TCPClient client) {
    this.client = client;
    client.addCloseListener(classCloser);
    httpProcessor = new HTTPResponseProcessor();
    httpProcessor.addHTTPRequestCallback(requestCB);
  }

  public HTTPStreamClient(SocketExecuter se, String host, int port, int timeout) throws IOException {
    client = se.createTCPClient(host, port);
    client.setConnectionTimeout(timeout);
    client.addCloseListener(classCloser);
    httpProcessor = new HTTPResponseProcessor();
    httpProcessor.addHTTPRequestCallback(requestCB);
  }

  /**
   * <p>When constructing the HTTPStreamClient we connect to a specific server.  If you 
   * want to use the HTTPRequest to set the server/port you can use the .getHost() and
   * .getPort() methods on it</p> 
   * 
   * @param host the hostname or ip address to connect to
   * @param port the tcp port to connect to
   * @param timeout how long to wait for the connection to be made
   * @throws IOException this will happen if we have problems connecting for some reason.
   */
  public HTTPStreamClient(SocketExecuter se, String host, int port, int timeout, boolean doSSL) throws IOException {
    client = se.createTCPClient(host, port);
    client.setConnectionTimeout(timeout);
    client.setSSLEngine(SSLUtils.OPEN_SSL_CTX.createSSLEngine(host, port));
    client.startSSL();
    client.setReader(classReader);
    client.addCloseListener(classCloser);
    httpProcessor = new HTTPResponseProcessor();
    httpProcessor.addHTTPRequestCallback(requestCB);
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
      slfResponse.setFailure(new Exception("New request came in!"));
    }
    currentHttpRequest = request;
    slfResponse = new SettableListenableFuture<HTTPResponse>();
    client.write(request.getByteBuffer());
    return slfResponse;
  }
  
  public ListenableFuture<?> write(ByteBuffer bb) {
    if(currentHttpRequest != null && currentHttpRequest.getHTTPHeaders().isChunked()) {
      return client.write(HTTPUtils.wrapInChunk(bb));
    } else {
      return client.write(bb);
    }
  }
  
  public void setHTTPStreamReader(HTTPStreamReader hsr) {
    this.httpReader.clearListeners();
    this.httpReader.addListener(hsr);
    client.setReader(classReader);
  }

  public Executor getClientsThreadExecutor() {
    //We use the clients executer to keep things single threaded between both clients
    return client.getClientsThreadExecutor();
  }

  public ListenableFuture<Boolean> connect() {
    return client.connect();
  }
  
  public void addCloseListener(CloseListener cl) {
    client.addCloseListener(cl);
  }

  public void close() {
    client.close();
  }  

  private class HTTPReader implements Reader {
    @Override
    public void onRead(Client client) {
      httpProcessor.processData(client.getRead());
    }
  }
  
  private class HTTPCloser implements CloseListener {
    @Override
    public void onClose(Client client) {
      //TODO:
    }
  }
  
  private class RequestCallback implements HTTPResponseCallback {

    @Override
    public void headersFinished(HTTPResponse hr) {
      slfResponse.setResult(hr);
    }

    @Override
    public void bodyData(ByteBuffer bb) {
      httpReader.call().handle(bb);
    }

    @Override
    public void finished() {
      client.close();
    }

    @Override
    public void hasError(Throwable t) {
      slfResponse.setFailure(t);
      client.close();
    }
    
  }
  
  public static interface HTTPStreamReader {
    public void handle(ByteBuffer bb);
  }
}
