package org.threadly.litesockets.protocol.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.protocol.http.structures.HTTPConstants;
import org.threadly.litesockets.protocol.http.structures.HTTPRequest;
import org.threadly.litesockets.protocol.http.structures.HTTPResponse;
import org.threadly.litesockets.protocol.http.structures.HTTPResponseProcessor;
import org.threadly.litesockets.protocol.http.structures.HTTPUtils;
import org.threadly.litesockets.tcp.SSLClient;
import org.threadly.litesockets.tcp.SSLUtils;
import org.threadly.litesockets.tcp.TCPClient;
import org.threadly.litesockets.utils.MergedByteBuffers;
import org.threadly.litesockets.utils.TransactionalByteBuffers;

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
public class HTTPStreamClient extends SSLClient {
  private static final int HEX_OCT = 16;
  private final MergedByteBuffers localMbb = new MergedByteBuffers();
  private final TransactionalByteBuffers tbb = new TransactionalByteBuffers();
  private volatile HTTPResponseProcessor httpProcessor;
  private volatile Reader localReader = null;
  private volatile HTTPRequest currentHttpRequest;
  private volatile SettableListenableFuture<HTTPResponse> slfResponse;
  private final Reader classReader = new HTTPReader();

  public HTTPStreamClient(String host, int port) throws IOException {
    this(host, port, TCPClient.DEFAULT_SOCKET_TIMEOUT, false);
  }

  public HTTPStreamClient(String host, int port, int timeout) throws IOException {
    this(host, port, timeout, false);
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
  public HTTPStreamClient(String host, int port, int timeout, boolean doSSL) throws IOException {
    super(host, port, SSLUtils.OPEN_SSL_CTX.createSSLEngine(host, port), timeout, doSSL);
    super.setReader(classReader);
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
    super.writeForce(request.getRequestBuffer());
    return slfResponse;
  }

  @Override
  public void writeForce(ByteBuffer bb) {
    if(currentHttpRequest.isChunked()) {
      super.writeForce(HTTPUtils.wrapInChunk(bb));
    } else {
      super.writeForce(bb);
    }
  }

  @Override
  public void setReader(Reader reader) {
    localReader = reader;
  }

  @Override
  public MergedByteBuffers getRead() {
    MergedByteBuffers mbb = new MergedByteBuffers();
    mbb.add(localMbb);
    return mbb;
  }

  private void parseRead() {
    MergedByteBuffers mbb = super.getRead();
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
        if(mbb.remaining() > 0 && localReader != null) {
          localMbb.add(mbb);
          localReader.onRead(this);
        }
      } else {
        tbb.add(mbb);
        int pos ;
        while((pos = tbb.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR)) >= 0) {
          tbb.begin();
          String tmp = tbb.getAsString(pos);
          int size = Integer.parseInt(tmp, HEX_OCT);
          tbb.discard(2);
          if(tbb.remaining() >= size+2) {
            if(localReader != null  && size > 0) {
              localMbb.add(tbb.pull(size));
              localReader.onRead(this);
            } else {
              tbb.discard(size);
            }
            tbb.discard(Math.min(2, tbb.remaining()));
            tbb.commit();
          } else {
            tbb.rollback();
            break;
          }
        }
      }
    }
  }

  /**
   * Runnable used to parse the readEvents from the client. 
   */
  private class HTTPReader implements Reader {
    @Override
    public void onRead(Client client) {
      parseRead();
    }
  }
}
