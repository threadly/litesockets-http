package org.threadly.litesockets.client.ws;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLEngine;

import org.threadly.concurrent.future.FutureCallback;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.TCPClient;
import org.threadly.litesockets.buffers.ReuseableMergedByteBuffers;
import org.threadly.litesockets.client.http.HTTPStreamClient;
import org.threadly.litesockets.client.http.HTTPStreamClient.HTTPStreamReader;
import org.threadly.litesockets.client.http.StreamingClient;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPRequestType;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.litesockets.protocols.ws.WebSocketFrameParser;
import org.threadly.litesockets.protocols.ws.WebSocketFrameParser.WebSocketFrame;
import org.threadly.litesockets.protocols.ws.WebSocketOpCode;
import org.threadly.litesockets.utils.IOUtils;


/**
 * This is a Wrapper around {@link HTTPStreamClient} that simplifies it for use with WebSockets.
 * 
 * @author lwahlmeier
 *
 */
public class WebSocketClient implements StreamingClient {
  public static final HTTPResponse DEFAULT_WS_RESPONSE = new HTTPResponseBuilder()
      .setResponseCode(HTTPResponseCode.SwitchingProtocols)
      .setHeader(HTTPConstants.HTTP_KEY_UPGRADE, "websocket")
      .setHeader(HTTPConstants.HTTP_KEY_CONNECTION, "Upgrade")
      .setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_ACCEPT, "123456")
      .build();
  public static final HTTPRequest DEFAULT_WS_REQUEST = new HTTPRequestBuilder()
      .setRequestType(HTTPRequestType.GET)
      .setHeader(HTTPConstants.HTTP_KEY_UPGRADE, "websocket")
      .setHeader(HTTPConstants.HTTP_KEY_CONNECTION, "Upgrade")
      .setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_VERSION, "13")
      .setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_KEY, "")
      .buildHTTPRequest(); 
  public static final String WSS_STRING = "wss";
  public static final String WS_STRING = "ws";
  public static final int WSS_PORT = 443;
  public static final int WS_PORT = 80;
  
  private final AtomicBoolean sentRequest = new AtomicBoolean(false);
  private final SettableListenableFuture<Boolean> connectFuture = new SettableListenableFuture<>();
  private final HTTPRequestBuilder hrb = new HTTPRequestBuilder();
  private final LocalStreamReader lsr = new LocalStreamReader();
  private final HTTPStreamClient hsc;
  
  private volatile WebSocketDataReader onData;
  private volatile WebSocketOpCode wsoc = WebSocketOpCode.Binary;
  private volatile boolean defaultMask = false;
  private volatile boolean autoReplyPings = true;

  /**
   * This takes over an existing TCPClient to do websocket communications. 
   * 
   * @param client the TCPClient to use for this connection.
   * @param alreadyUpgraded true if the connection has already upgraded to do websockets false if the http upgrade is still required.
   */
  public WebSocketClient(final TCPClient client) {
    if(client.isClosed()) {
      throw new IllegalStateException("TCPClient is closed! Can only use an Open TCPClient");
    }
    
    hsc = new HTTPStreamClient(client);
    connectFuture.setResult(true);
  }
  
  /**
   * Creates a websocket connection to the specified {@link URI}.
   * 
   * @param se the {@link SocketExecuter} to use for creating this connection.
   * @param uri the {@link URI} to use for setting up this connection.
   * @throws IOException this is thrown if there are any problems creating the TCP socket for this connection.
   */
  public WebSocketClient(final SocketExecuter se, final URI uri) throws IOException {
    int port = 0; 
    if(uri.getPort() > 0) {
      port = uri.getPort();
    } else {
      if(uri.getScheme().equalsIgnoreCase(WS_STRING)) {
        port = WS_PORT;
      } else if(uri.getScheme().equalsIgnoreCase(WSS_STRING)) {
        port = WSS_PORT;
      }
    }
    hsc = new HTTPStreamClient(se, uri.getHost(), port);
    if(uri.getScheme().equalsIgnoreCase(WSS_STRING)) {
      hsc.enableSSL();
    }
    makeDefaultBuilder();
    this.hrb.setPath(uri.getPath());
    if(uri.getRawQuery() != null && !uri.getRawQuery().equals("")) {
      this.hrb.setQueryString(uri.getRawQuery());
    }
  }

  /**
   * Creates a websocket connection to the specified host and port, using the provided {@link SocketExecuter}.
   * 
   * @param se the {@link SocketExecuter} to use for this connection.
   * @param host the host string or IP to make the connection too.
   * @param port the port on the host to connect too.
   * @throws IOException this is thrown if there are any problems creating the tcp socket for this connection. 
   */
  public WebSocketClient(final SocketExecuter se, final String host, final int port) throws IOException {
    hsc = new HTTPStreamClient(se, host, port);
    makeDefaultBuilder();
  }

  private void makeDefaultBuilder() {
    hrb.setHeader(HTTPConstants.HTTP_KEY_UPGRADE, "websocket")
    .setHeader(HTTPConstants.HTTP_KEY_CONNECTION, HTTPConstants.HTTP_KEY_UPGRADE)
    .setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_VERSION, "13")
    .setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_KEY, WebSocketFrameParser.makeSecretKey())
    .setHost(hsc.getHost());
  }
  
  @Override
  public void enableSSL() {
    if(!sentRequest.get()) {
      hsc.enableSSL();
    }
  }
  
  @Override
  public void enableSSL(final SSLEngine ssle) {
    if(!sentRequest.get()) {
      hsc.enableSSL(ssle);
    }
  }
  
  @Override
  public void setConnectionTimeout(final int timeout) {
    if(!sentRequest.get()) {
      hsc.setConnectionTimeout(timeout);
    }
  }

  /**
   * This sets whether or not the default {@link #write(ByteBuffer)} should mask the data that is being sent. 
   * 
   * @param mask true to set the default to mask the sent data, false to not mask the data.
   */
  public void setDefaultMask(final boolean mask) {
    this.defaultMask = mask;
  }  
  
  /**
   * Returns the value of the default mask set on this WebSocketClient.
   * 
   * @return true if masking is done by default, false if its not. 
   */
  public boolean getDefaultMask() {
    return this.defaultMask;
  }
  
  /**
   * This sets whether or not the WebSocketClient will auto replay to websocket pings.  
   * If this is set to false the {@link WebSocketDataReader onData(WebSocketFrame, ByteBuffer)} 
   * call will get the pings and they must be handled manually.
   * 
   * 
   * @param doPings set to true if the WebSocketClient should automatically reply to pings, false to handle them manually.
   */
  public void doPingAutoPong(final boolean doPings) {
    this.autoReplyPings = doPings;
  }
  
  /**
   * Returns the current value set for pingAuto reply. Default value is true.
   * 
   * @return true if the WebSocketClient will auto reply to pings, false if they are to be handled manually.
   */
  public boolean getPingAutoReply() {
    return this.autoReplyPings;
  }
  
  /**
   * Sets the default {@link WebSocketOpCode} to use when calling {@link #write(ByteBuffer)}.
   * 
   * Only standard WebSocket OpCodes can be used as a "default" to use anything other then the 
   * standard OpCodes use the {@link #write(ByteBuffer, byte, boolean)} method.
   * 
   * @param wsoc the default {@link WebSocketOpCode} to use on this connection.
   */
  public void setDefaultOpCode(WebSocketOpCode wsoc) {
    this.wsoc = wsoc;
  }
  
  /**
   * Returns the current default {@link WebSocketOpCode} in use by this connection.
   * 
   * @return the {@link WebSocketOpCode} currently used by default. 
   */
  public WebSocketOpCode getDefaultOpCode() {
    return this.wsoc;
  }

  /**
   * This allows you to set the path used in the initial HTTP upgrade Request sent to the WebSocket server.
   * This path can also include the query portion, and that will also be set in the request.
   * NOTE: This must be set before {@link #connect()} is called.
   * 
   * @param path the path to use when doing the initial HTTP Request.
   */
  public void setRequestPath(final String path) {
    if(!sentRequest.get()) {
      hrb.setPath(path);
    }
  }
  
  /**
   * This allows you to set the websocket key used in the initial HTTP upgrade request sent to the server.
   * NOTE: This must be set before {@link #connect()} is called.
   * 
   * @param key the key to use for the upgrade request.
   */
  public void setWebSocketKey(final String key) {
    if(!sentRequest.get()) {
      hrb.setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_KEY, key);
    }
  }

  /**
   * This allows you to set the websocket version used in the initial HTTP upgrade request.
   * Be careful with this, this current implementation only parse the current standard, version 13. 
   * NOTE: This must be set before {@link #connect()} is called.
   * 
   * @param version the version to use.
   */
  public void setWebSocketVersion(final int version) {
    if(!sentRequest.get()) {
      hrb.setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_VERSION, Integer.toString(version));
    }
  }

  /**
   * This allows you to set extra headers or change any header on the HTTP upgrade request sent to the server.
   * NOTE: This must be set before {@link #connect()} is called.
   * NOTE: You can override any header here, make sure you know what your doing!
   * 
   * @param key The HTTP HeaderKey. 
   * @param value The HTTP HeaderValue. 
   */
  public void setExtraHeader(final String key, final String value) {
    if(!sentRequest.get()) {
      hrb.setHeader(key, value);
    }
  }

  /**
   * Sets the {@link WebSocketDataReader} for this client.  This will be used for callbacks when full 
   * websocket frames are received.  These call backs will happen in order and in a thread safe way (per client).
   * 
   * NOTE: this can be set to null but if it is all reads for this client will be blocked till its set again.
   * 
   * @param reader the {@link WebSocketDataReader} to use for this client.
   */
  public void setWebSocketDataReader(final WebSocketDataReader reader) {
    onData = reader;
    hsc.setHTTPStreamReader(lsr);
  }
  

  @Override
  public void setRequestResponseHeaders(HTTPRequest httpRequest, HTTPResponse httpResponse, boolean writeRequest) {
    hsc.setRequestResponseHeaders(httpRequest, httpResponse, writeRequest);
    sentRequest.set(true);
  }
  
  @Override
  public ListenableFuture<?> write(final ByteBuffer bb) {
    return write(bb, this.wsoc, defaultMask);
  }
  
  @Override
  public Executor getClientsThreadExecutor() {
    return this.hsc.getClientsThreadExecutor();
  }
  
  /**
   * This performs a write to the websocket connection.  This write will use the provided mask and OpCode values, ignoring the 
   * defaults.
   * 
   * Every {@link ByteBuffer} written is seen as an individual websocketFrame.
   * 
   * @param bb the {@link ByteBuffer} to write to frame and write to the websocket.
   * @param opCode the opCode to use in the websocket frame.
   * @param mask sets whether or not to mask the websocket data. true to mask, false to not.
   * @return a {@link ListenableFuture} that will be completed once the frame has been fully written to the socket.
   */
  public ListenableFuture<?> write(final ByteBuffer bb, final WebSocketOpCode opCode, final boolean mask) {
    if(connectFuture.isDone()) {
      WebSocketFrame wsFrame = WebSocketFrameParser.makeWebSocketFrame(bb.remaining(), opCode, mask);
      ByteBuffer data = bb;
      if(mask) {
        data = wsFrame.unmaskPayload(bb);
      }
      synchronized(this) {
        hsc.write(wsFrame.getRawFrame());
        return hsc.write(data);
      }
    } else {
      throw new IllegalStateException("Must be connected first!");
    }
  }
  
  public ListenableFuture<?> getLastWriteFuture() {
    return hsc.getLastWriteFuture();
  }

  @Override
  public ListenableFuture<Boolean> connect() {
    if(sentRequest.compareAndSet(false, true)) {
      hsc.connect();
      hsc.writeRequest(hrb.buildHTTPRequest()).addCallback(new FutureCallback<HTTPResponse>() {
        @Override
        public void handleResult(HTTPResponse result) {
          if(result.getResponseHeader().getResponseCode() == HTTPResponseCode.SwitchingProtocols) {
            String orig = hrb.buildHTTPRequest().getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_KEY);
            String resp = result.getHeaders().getHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_ACCEPT);
              if(WebSocketFrameParser.validateKeyResponse(orig, resp)) {
                connectFuture.setResult(true);
              } else {
                connectFuture.setFailure(
                    new IllegalStateException("Bad WebSocket Key Response!: "+ resp 
                        +": Should be:"+WebSocketFrameParser.makeKeyResponse(orig)));
                hsc.close();
              }
          } else {
            connectFuture.setFailure(new IllegalStateException("Protcol not upgraded!"));
            hsc.close();
          }
        }

        @Override
        public void handleFailure(Throwable t) {
          connectFuture.setFailure(t);
          hsc.close();
        }});
    }
    return connectFuture;
  }
  
  @Override
  public boolean isConnected() {
    return hsc.isConnected();
  }
  
  @Override
  public void close() {
    hsc.close();
  }
  
  @Override
  public void addCloseListener(final Runnable cl) {
    hsc.addCloseListener(cl);
  }

  /**
   * 
   * @author lwahlmeier
   *
   */
  private class LocalStreamReader implements HTTPStreamReader {
    private final ReuseableMergedByteBuffers mbb = new ReuseableMergedByteBuffers();
    private WebSocketFrame lastFrame;

    @Override
    public void handle(final ByteBuffer bb) {
      mbb.add(bb);
      while(mbb.remaining() > 0) {
        try {
          if(lastFrame == null) {
            lastFrame = WebSocketFrameParser.parseWebSocketFrame(mbb);
          }
          if(lastFrame != null) {
            if(mbb.remaining() >= lastFrame.getPayloadDataLength()) {
              ByteBuffer data = mbb.pullBuffer((int) lastFrame.getPayloadDataLength());
              if(lastFrame.hasMask()) {
                data = lastFrame.unmaskPayload(data);
              }
              if(autoReplyPings && lastFrame.getOpCode() == WebSocketOpCode.Ping.getValue()) {
                write(IOUtils.EMPTY_BYTEBUFFER, WebSocketOpCode.Pong, false);
              } else {
                onData.onData(lastFrame, data);
              }
              lastFrame = null;
            } else {
              break;
            }
          }
        } catch(ParseException e) {
          break;
        }
      }
    }
  }

  /**
   * This is the Read callback used for {@link WebSocketClient}.
   * 
   * @author lwahlmeier
   *
   */
  public interface WebSocketDataReader {
    /**
     * This is called when a data frame is read off the {@link WebSocketClient}.
     * 
     * @param wsf the {@link WebSocketFrame} that was read off the socket.
     * @param bb the payload of the frame, might be empty, but never null.
     */
    public void onData(WebSocketFrame wsf, ByteBuffer bb);
  }


}
