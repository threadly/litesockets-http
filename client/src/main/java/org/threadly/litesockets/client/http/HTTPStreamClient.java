package org.threadly.litesockets.client.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.WireProtocol;
import org.threadly.litesockets.utils.SSLUtils;
import org.threadly.litesockets.TCPClient;
import org.threadly.litesockets.utils.MergedByteBuffers;
import org.threadly.litesockets.utils.TransactionalByteBuffers;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseProcessor;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;

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
public class HTTPStreamClient extends Client {
  private static final int HEX_OCT = 16;
  private final MergedByteBuffers localMbb = new MergedByteBuffers();
  private final TransactionalByteBuffers tbb = new TransactionalByteBuffers();
  private final Reader classReader = new HTTPReader();
  private final CloseListener classCloser = new HTTPCloser();
  private final TCPClient client;
  
  private volatile HTTPResponseProcessor httpProcessor;
  private volatile HTTPRequest currentHttpRequest;
  private volatile SettableListenableFuture<HTTPResponse> slfResponse;

  public HTTPStreamClient(TCPClient client) {
    super(client.getClientsSocketExecuter());
    this.client = client;
    client.setReader(classReader);
    client.addCloseListener(classCloser);
  }

  public HTTPStreamClient(SocketExecuter se, String host, int port, int timeout) throws IOException {
    super(se);
    client = se.createTCPClient(host, port);
    client.setConnectionTimeout(timeout);
    client.setReader(classReader);
    client.addCloseListener(classCloser);
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
    super(se);
    client = se.createTCPClient(host, port);
    client.setConnectionTimeout(timeout);
    client.setSSLEngine(SSLUtils.OPEN_SSL_CTX.createSSLEngine(host, port));
    client.startSSL();
    client.setReader(classReader);
    client.addCloseListener(classCloser);
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
    httpProcessor = new HTTPResponseProcessor();
    slfResponse = new SettableListenableFuture<HTTPResponse>();
    httpProcessor.disableChunkParser();
    localMbb.discard(localMbb.remaining());
    tbb.discard(tbb.remaining());
    client.write(request.getCombinedBuffers());
    return slfResponse;
  }
  
  @Override
  public MergedByteBuffers getRead() {
    return localMbb.duplicateAndClean();
  }

  private void parseRead() {
    MergedByteBuffers mbb = client.getRead();
    if(!httpProcessor.headersDone()) {
      httpProcessor.process(mbb);
      mbb.discard(mbb.remaining());
    }
    if(httpProcessor.headersDone()) {
      if(!slfResponse.isDone()) {
        slfResponse.setResult(httpProcessor.getResponseHeadersOnly());
        if(httpProcessor.getBodyLength() > 0) {
          ByteBuffer body = httpProcessor.pullBody();
          ByteBuffer remaining = httpProcessor.getExtraBuffers();
          if(remaining.remaining() > 0) {
            mbb.add(body);
            mbb.add(remaining);
          } else {
            mbb.add(body);
          }
        } else {
          ByteBuffer bb = httpProcessor.getExtraBuffers();
          mbb.add(bb);
        }
      }
      
      if(!httpProcessor.isChunked()) {
        if(mbb.remaining() > 0) {
          localMbb.add(mbb);
          callReader();
        }
      } else {
        tbb.add(mbb);
        int pos ;
        boolean didWrite = false;
        boolean doClose = false;
        while((pos = tbb.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR)) >= 0) {
          tbb.begin();
          String tmp = tbb.getAsString(pos);
          int size = Integer.parseInt(tmp, HEX_OCT);
          System.out.println(size+":"+tbb.remaining());
          if(size == 0) {
            doClose = true;
            tbb.rollback();
            break;
          }
          tbb.discard(2);
          if(tbb.remaining() >= size+2) {
            if(size > 0) {
              localMbb.add(tbb.pull(size));
              didWrite = true;
            }
            tbb.discard(Math.min(2, tbb.remaining()));
            tbb.commit();
          } else {
            System.out.println("rollback");
            tbb.rollback();
            break;
          }
        }
        if(didWrite) {
          callReader();
        }
        if(doClose) {
          callClosers();
        }
      }
    }
  }
  
  @Override
  public Executor getClientsThreadExecutor() {
    return client.getClientsThreadExecutor();
  }

  /**
   * Runnable used to parse the readEvents from the client. 
   */
  private class HTTPReader implements Reader {
    @Override
    public void onRead(Client client) {
      parseRead();
      callReader();
    }
  }
  
  private class HTTPCloser implements CloseListener {
    @Override
    public void onClose(Client client) {
      callClosers();
    }
  }

  @Override
  public boolean canWrite() {
    return client.canWrite();
  }

  @Override
  public boolean hasConnectionTimedOut() {
    return client.hasConnectionTimedOut();
  }

  @Override
  public boolean setSocketOption(SocketOption so, int value) {
    return client.setSocketOption(so, value);
  }

  @Override
  public ListenableFuture<Boolean> connect() {
    return client.connect();
  }

  @Override
  protected void setConnectionStatus(Throwable t) {

  }

  @Override
  public void setConnectionTimeout(int timeout) {
    client.setConnectionTimeout(timeout);
  }

  @Override
  public int getTimeout() {
    return client.getTimeout();
  }

  @Override
  public int getWriteBufferSize() {
    return client.getWriteBufferSize();
  }

  @Override
  public ListenableFuture<?> write(ByteBuffer bb) {
    return client.write(bb);
  }

  @Override
  protected ByteBuffer getWriteBuffer() {
    return null;
  }

  @Override
  protected void reduceWrite(int size) {
    
  }

  @Override
  protected SocketChannel getChannel() {
    return null;
  }

  @Override
  public WireProtocol getProtocol() {
    return client.getProtocol();
  }

  @Override
  protected Socket getSocket() {
    return null;
  }

  @Override
  public void close() {
    client.close();
  }

  @Override
  public InetSocketAddress getRemoteSocketAddress() {
    return client.getRemoteSocketAddress();
  }

  @Override
  public InetSocketAddress getLocalSocketAddress() {
    return client.getLocalSocketAddress();
  }
}
