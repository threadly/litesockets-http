package org.threadly.litesockets.client.ws;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLEngine;

import org.threadly.concurrent.future.FutureCallback;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.TCPClient;
import org.threadly.litesockets.client.http.HTTPStreamClient;
import org.threadly.litesockets.client.http.HTTPStreamClient.HTTPStreamReader;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.litesockets.protocols.ws.WebSocketFrameParser;
import org.threadly.litesockets.protocols.ws.WebSocketFrameParser.WebSocketFrame;
import org.threadly.litesockets.utils.MergedByteBuffers;

public class WebSocketClient {
  public static final String WSS_STRING = "wss";
  public static final String WS_STRING = "ws";
  public static final int WSS_PORT = 443;
  public static final int WS_PORT = 80;
  
  private final AtomicBoolean sentRequest = new AtomicBoolean(false);
  private final SettableListenableFuture<Boolean> connectFuture = new SettableListenableFuture<Boolean>();
  private final HTTPRequestBuilder hrb = new HTTPRequestBuilder();
  private final LocalStreamReader lsr = new LocalStreamReader();
  private final HTTPStreamClient hsc;
  private volatile WebSocketDataReader onData;

  public WebSocketClient(TCPClient client, boolean alreadyUpgraded) {
    hsc = new HTTPStreamClient(client);
    if(alreadyUpgraded) {
      sentRequest.set(true);
      connectFuture.setResult(true);
    }
  }
  
  public WebSocketClient(SocketExecuter SE, URI url) throws IOException {
    int port = 0; 
    if(url.getPort() > 0) {
      port = url.getPort();
    } else {
      if(url.getScheme().equalsIgnoreCase(WS_STRING)) {
        port = WS_PORT;
      } else if(url.getScheme().equalsIgnoreCase(WSS_STRING)) {
        port = WSS_PORT;
      }
    }
    hsc = new HTTPStreamClient(SE, url.getHost(), port);
    if(url.getScheme().equalsIgnoreCase(WSS_STRING)) {
      hsc.enableSSL();
    }
    makeDefaultBuilder();
  }

  public WebSocketClient(SocketExecuter SE, String host, int port) throws IOException {
    hsc = new HTTPStreamClient(SE, host, port);
    makeDefaultBuilder();
  }

  private void makeDefaultBuilder() {
    hrb.setHeader(HTTPConstants.HTTP_KEY_UPGRADE, "websocket")
    .setHeader(HTTPConstants.HTTP_KEY_CONNECTION, HTTPConstants.HTTP_KEY_UPGRADE)
    .setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_VERSION, "13")
    .setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_KEY, WebSocketFrameParser.makeSecretKey())
    .setHost(hsc.getHost());
  }
  
  public void enableSSL() {
    hsc.enableSSL();
  }
  
  public void enableSSL(SSLEngine ssle) {
    hsc.enableSSL(ssle);
  }
  
  public void setTimeout(int timeout) {
    hsc.setTimeout(timeout);
  }

  public void setWebSocketKey(String key) {
    if(!sentRequest.get()) {
      hrb.setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_KEY, key);
    }
  }

  public void setWebSocketVersion(int version) {
    if(!sentRequest.get()) {
      hrb.setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_VERSION, Integer.toString(version));
    }
  }

  public void setExtraHeader(String key, String value) {
    if(!sentRequest.get()) {
      hrb.setHeader(key, value);
    }
  }

  public void setWebSocketDataReader(WebSocketDataReader reader) {
    if(reader != null) {
      onData = reader;
      hsc.setHTTPStreamReader(lsr);
    }
  }
  
  public ListenableFuture<?> write(ByteBuffer bb, byte obCode, boolean mask) {
    if(connectFuture.isDone()) {
      WebSocketFrame wsFrame = WebSocketFrameParser.makeWebSocketFrame(bb.remaining(), obCode, mask);
      ByteBuffer data = bb;
      if(mask) {
        data = wsFrame.unmaskPayload(bb);
      }
      hsc.write(wsFrame.getRawFrame());
      return hsc.write(data);
    } else {
      throw new IllegalStateException("Must be connected first!");
    }
  }

  public ListenableFuture<Boolean> connect() {
    if(sentRequest.compareAndSet(false, true)) {
      hsc.connect();
      hsc.writeRequest(hrb.build()).addCallback(new FutureCallback<HTTPResponse>() {

        @Override
        public void handleResult(HTTPResponse result) {
          if(result.getResponseHeader().getResponseCode() == HTTPResponseCode.SwitchingProtocols) {
            String orig = hrb.build().getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_KEY);
            String resp = result.getHeaders().getHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_ACCEPT);
              if(WebSocketFrameParser.validateKeyResponse(orig, resp)) {
                connectFuture.setResult(true);
              } else {
                connectFuture.setFailure(new IllegalStateException("Bad WebSocket Key Response!: "+ resp +": Should be:"+WebSocketFrameParser.makeKeyResponse(orig)));
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
  
  public boolean isConnected() {
    return hsc.isConnected();
  }
  
  public void close() {
    hsc.close();
  }
  
  public void addCloseListener(Runnable cl) {
    hsc.addCloseListener(cl);
  }

  private class LocalStreamReader implements HTTPStreamReader {

    private final MergedByteBuffers mbb = new MergedByteBuffers();
    private WebSocketFrame lastFrame;

    @Override
    public void handle(ByteBuffer bb) {
      mbb.add(bb);
      while(mbb.remaining() > 0) {
        try {
          if(lastFrame == null) {
            lastFrame = WebSocketFrameParser.parseWebSocketFrame(mbb);
          } else {
            if(mbb.remaining() >= lastFrame.getPayloadDataLength()) {
              ByteBuffer data = mbb.pull((int) lastFrame.getPayloadDataLength());
              if(lastFrame.hasMask()) {
                data = lastFrame.unmaskPayload(data);
              }
              onData.onData(data);
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

  public interface WebSocketDataReader {
    public void onData(ByteBuffer bb);
  }
}
