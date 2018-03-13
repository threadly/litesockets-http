package org.threadly.litesockets.client.ws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.future.FutureCallback;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.Client.Reader;
import org.threadly.litesockets.Server.ClientAcceptor;
import org.threadly.litesockets.TCPClient;
import org.threadly.litesockets.TCPServer;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.client.ws.WebSocketClient.WebSocketDataReader;
import org.threadly.litesockets.protocols.http.response.HTTPResponseBuilder;
import org.threadly.litesockets.protocols.http.response.HTTPResponseHeader;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.litesockets.protocols.ws.WebSocketFrameParser;
import org.threadly.litesockets.protocols.ws.WebSocketFrameParser.WebSocketFrame;
import org.threadly.litesockets.protocols.ws.WebSocketOpCode;
import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.buffers.ReuseableMergedByteBuffers;
import org.threadly.litesockets.utils.PortUtils;
import org.threadly.test.concurrent.TestCondition;
import org.threadly.util.ExceptionUtils;

public class WebSocketClientTest {
  PriorityScheduler PS;
  ThreadedSocketExecuter TSE;
  TCPServer httpServer;
  int port;

  @Before
  public void start() throws IOException {
    port = PortUtils.findTCPPort();
    PS = new PriorityScheduler(2);
    TSE = new ThreadedSocketExecuter(PS);
    TSE.start();
    httpServer = TSE.createTCPServer("localhost", port);
    httpServer.start();
    httpServer.start();
  }

  @After
  public void stop() {
    TSE.stopIfRunning();
    PS.shutdownNow();
    httpServer.close();
  }

  @Test
  public void simpleConnectTest() throws IOException, URISyntaxException {
    httpServer.setClientAcceptor(new WSEchoHandler());
    final AtomicReference<String> response = new AtomicReference<String>(null);
    final WebSocketClient wsc = new WebSocketClient(TSE, new URI("ws://localhost:"+port+"/?test=2"));
    wsc.setWebSocketDataReader(new WebSocketDataReader() {
      @Override
      public void onData(WebSocketFrame wsf, ByteBuffer bb) {
        ReuseableMergedByteBuffers mbb = new ReuseableMergedByteBuffers();
        mbb.add(bb);
        response.compareAndSet(null, mbb.getAsString(mbb.remaining()));
      }});
    wsc.connect().addCallback(new FutureCallback<Boolean>(){
      @Override
      public void handleResult(Boolean result) {
        wsc.write(ByteBuffer.wrap("ECHO".getBytes()), WebSocketOpCode.Text, false);
      }

      @Override
      public void handleFailure(Throwable t) {
        t.printStackTrace();
        fail(ExceptionUtils.stackToString(t));        
      }});

    new TestCondition(){
      @Override
      public boolean get() {
        return response.get() != null;
      }
    }.blockTillTrue(3000);
    assertEquals("ECHO", response.get());
    assertTrue(wsc.isConnected());
    response.set(null);
    wsc.write(ByteBuffer.wrap("ECHO".getBytes()), WebSocketOpCode.Text, true);
    new TestCondition(){
      @Override
      public boolean get() {
        return response.get() != null;
      }
    }.blockTillTrue(3000);
    assertEquals("ECHO", response.get());
    wsc.close();
    assertFalse(wsc.isConnected());

  }
  
  
  
  @Test
  public void preConnectTest() throws IOException, URISyntaxException, InterruptedException, ExecutionException, TimeoutException {
    httpServer.setClientAcceptor(new WSEchoHandler());
    final AtomicReference<String> response = new AtomicReference<String>(null);
    final SettableListenableFuture<MergedByteBuffers> slf = new SettableListenableFuture<>();
    TCPClient WSclient = TSE.createTCPClient("localhost", port);
    WSclient.setReader((c)->{
      slf.setResult(c.getRead());
    });
    WSclient.connect().get(10, TimeUnit.SECONDS);
    WSclient.write(WebSocketClient.DEFAULT_WS_REQUEST.getByteBuffer());
    slf.get(10, TimeUnit.SECONDS);
    
    final WebSocketClient wsc = new WebSocketClient(WSclient);
    
    wsc.setRequestResponseHeaders(WebSocketClient.DEFAULT_WS_REQUEST, WebSocketClient.DEFAULT_WS_RESPONSE, false);
    
    wsc.setWebSocketDataReader(new WebSocketDataReader() {
      @Override
      public void onData(WebSocketFrame wsf, ByteBuffer bb) {
        ReuseableMergedByteBuffers mbb = new ReuseableMergedByteBuffers();
        mbb.add(bb);
        response.compareAndSet(null, mbb.getAsString(mbb.remaining()));
      }});
    wsc.write(ByteBuffer.wrap("ECHO".getBytes()), WebSocketOpCode.Text, false);


    new TestCondition(){
      @Override
      public boolean get() {
        return response.get() != null;
      }
    }.blockTillTrue(3000);
    assertEquals("ECHO", response.get());
    assertTrue(wsc.isConnected());
    response.set(null);
    wsc.write(ByteBuffer.wrap("ECHO".getBytes()), WebSocketOpCode.Text, true);
    new TestCondition(){
      @Override
      public boolean get() {
        return response.get() != null;
      }
    }.blockTillTrue(3000);
    assertEquals("ECHO", response.get());
    wsc.close();
    assertFalse(wsc.isConnected());

  }

  @Test
  public void badKeyResponseTest() throws IOException, URISyntaxException {
    httpServer.setClientAcceptor(new BadKeyResponseHandler());
    final AtomicBoolean gotFailure = new AtomicBoolean(false);
    final AtomicBoolean gotClose = new AtomicBoolean(false);
    final WebSocketClient wsc = new WebSocketClient(TSE, new URI("ws://localhost:"+port));
    wsc.setWebSocketDataReader(new WebSocketDataReader() {
      @Override
      public void onData(WebSocketFrame wsf, ByteBuffer bb) {

      }});
    wsc.addCloseListener(new Runnable() {

      @Override
      public void run() {
        gotClose.set(true);
      }});
    wsc.connect().addCallback(new FutureCallback<Boolean>(){
      @Override
      public void handleResult(Boolean result) {
      }

      @Override
      public void handleFailure(Throwable t) {
        gotFailure.set(true);
      }});

    new TestCondition(){
      @Override
      public boolean get() {
        return gotFailure.get();
      }
    }.blockTillTrue(3000);
    assertTrue(gotFailure.get());
    new TestCondition(){
      @Override
      public boolean get() {
        return gotClose.get();
      }
    }.blockTillTrue(3000);
    assertTrue(gotClose.get());
    assertFalse(wsc.isConnected());
  }
  
  @Test
  public void badHeaderResponseTest() throws IOException, URISyntaxException {
    httpServer.setClientAcceptor(new BadResponseHeaderHandler());
    final AtomicBoolean gotFailure = new AtomicBoolean(false);
    final AtomicBoolean gotClose = new AtomicBoolean(false);
    final WebSocketClient wsc = new WebSocketClient(TSE, new URI("ws://localhost:"+port));
    wsc.setWebSocketDataReader(new WebSocketDataReader() {
      @Override
      public void onData(WebSocketFrame wsf, ByteBuffer bb) {

      }});
    wsc.addCloseListener(new Runnable() {

      @Override
      public void run() {
        gotClose.set(true);
      }});
    wsc.connect().addCallback(new FutureCallback<Boolean>(){
      @Override
      public void handleResult(Boolean result) {
      }

      @Override
      public void handleFailure(Throwable t) {
        gotFailure.set(true);
      }});

    new TestCondition(){
      @Override
      public boolean get() {
        return gotFailure.get();
      }
    }.blockTillTrue(3000);
    assertTrue(gotFailure.get());
    new TestCondition(){
      @Override
      public boolean get() {
        return gotClose.get();
      }
    }.blockTillTrue(3000);
    assertTrue(gotClose.get());
    assertFalse(wsc.isConnected());
  }

  public class BadResponseHeaderHandler implements ClientAcceptor, Reader {
    final ConcurrentHashMap<Client, MergedByteBuffers> buffers = new ConcurrentHashMap<Client, MergedByteBuffers>(); 
    final ConcurrentHashMap<Client, Boolean> headerDone = new ConcurrentHashMap<Client, Boolean>();


    @Override
    public void accept(Client client) {
      buffers.put(client, new ReuseableMergedByteBuffers());
      headerDone.put(client, false);
      client.setReader(this);
    }

    @Override
    public void onRead(Client client) {
      if(!headerDone.get(client)) {
        MergedByteBuffers mbb = buffers.get(client);
        mbb.add(client.getRead());
        System.out.println(mbb.duplicate().getAsString(mbb.remaining()));
        if(mbb.indexOf("\r\n\r\n") > -1) {
          headerDone.put(client, true);
          String[] request = mbb.getAsString(mbb.indexOf("\r\n\r\n")).split("\r\n");
          mbb.discard(4);
          String respKey = null;
          for(String s: request) {
            if(s.contains(HTTPConstants.HTTP_KEY_WEBSOCKET_KEY)) {
              String[] tmp = s.split(":");
              respKey = WebSocketFrameParser.makeKeyResponse(tmp[1].trim());
            }
          }
          HTTPResponseBuilder hrb = new HTTPResponseBuilder().setResponseHeader(new HTTPResponseHeader(HTTPResponseCode.NotFound, HTTPConstants.HTTP_VERSION_1_1));
          hrb.setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_ACCEPT, respKey);
          hrb.setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, null);
          client.write(hrb.build().getByteBuffer());
          if(mbb.remaining() > 0) {
            client.write(mbb.pullBuffer(mbb.remaining()));
          }
        }
      } else {
        MergedByteBuffers mbb = client.getRead();
        if(mbb.remaining() > 0) {
          client.write(mbb.pullBuffer(mbb.remaining()));
        }
      }
    }
  }


  public class BadKeyResponseHandler implements ClientAcceptor, Reader {
    final ConcurrentHashMap<Client, MergedByteBuffers> buffers = new ConcurrentHashMap<Client, MergedByteBuffers>(); 
    final ConcurrentHashMap<Client, Boolean> headerDone = new ConcurrentHashMap<Client, Boolean>();


    @Override
    public void accept(Client client) {
      buffers.put(client, new ReuseableMergedByteBuffers());
      headerDone.put(client, false);
      client.setReader(this);
    }

    @Override
    public void onRead(Client client) {
      HTTPResponseBuilder hrb = new HTTPResponseBuilder().setResponseHeader(new HTTPResponseHeader(HTTPResponseCode.SwitchingProtocols, HTTPConstants.HTTP_VERSION_1_1));
      hrb.setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_ACCEPT, "BADKEY");
      hrb.setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, null);
      client.write(hrb.build().getByteBuffer());
    }
  }

  public class WSEchoHandler implements ClientAcceptor, Reader {
    final ConcurrentHashMap<Client, MergedByteBuffers> buffers = new ConcurrentHashMap<Client, MergedByteBuffers>(); 
    final ConcurrentHashMap<Client, Boolean> headerDone = new ConcurrentHashMap<Client, Boolean>();


    @Override
    public void accept(Client client) {
      buffers.put(client, new ReuseableMergedByteBuffers());
      headerDone.put(client, false);
      client.setReader(this);
    }

    @Override
    public void onRead(Client client) {
      if(!headerDone.get(client)) {
        MergedByteBuffers mbb = buffers.get(client);
        mbb.add(client.getRead());
        System.out.println(mbb.duplicate().getAsString(mbb.remaining()));
        if(mbb.indexOf("\r\n\r\n") > -1) {
          headerDone.put(client, true);
          String[] request = mbb.getAsString(mbb.indexOf("\r\n\r\n")).split("\r\n");
          mbb.discard(4);
          String respKey = null;
          for(String s: request) {
            if(s.contains(HTTPConstants.HTTP_KEY_WEBSOCKET_KEY)) {
              String[] tmp = s.split(":");
              respKey = WebSocketFrameParser.makeKeyResponse(tmp[1].trim());
            }
          }
          HTTPResponseBuilder hrb = new HTTPResponseBuilder().setResponseHeader(new HTTPResponseHeader(HTTPResponseCode.SwitchingProtocols, HTTPConstants.HTTP_VERSION_1_1));
          hrb.setHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_ACCEPT, respKey);
          hrb.setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, null);
          client.write(hrb.build().getByteBuffer());
          if(mbb.remaining() > 0) {
            client.write(mbb.pullBuffer(mbb.remaining()));
          }
        }
      } else {
        MergedByteBuffers mbb = client.getRead();
        if(mbb.remaining() > 0) {
          client.write(mbb.pullBuffer(mbb.remaining()));
        }
      }
    }

  }
}
