package org.threadly.litesockets.client.http;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.threadly.concurrent.PriorityScheduler;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.TCPServer;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.buffers.TransactionalByteBuffers;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestProcessor;
import org.threadly.litesockets.protocols.http.request.HTTPRequestProcessor.HTTPRequestCallback;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.websocket.WSFrame;
import org.threadly.litesockets.utils.SSLUtils.FullTrustManager;

public class FakeHTTPStreamingServer  {
  public static byte[] SEND_DATA = new byte[1024];
  TrustManager[] myTMs = new TrustManager [] {new FullTrustManager() };
  private final ConcurrentHashMap<Client, HTTPRequestProcessor> clients = new ConcurrentHashMap<>();
  KeyStore KS;
  KeyManagerFactory kmf;
  SSLContext sslCtx;
  PriorityScheduler PS;
  ThreadedSocketExecuter SEB;
  int port;
  TCPServer server;
  TransactionalByteBuffers sendBack = new TransactionalByteBuffers();
  int kToSend;
  boolean chunked;
  HTTPResponse hr;

  public FakeHTTPStreamingServer(int port, HTTPResponse hr, String sendBack, int kiloToSend, boolean doSSL, boolean chunk) throws IOException {
    this.port = port;
    this.hr = hr;
    this.chunked = chunk;
    kToSend = kiloToSend;
    if(doSSL) {
      doSSLCrap();
    }
    PS = new PriorityScheduler(5);
    SEB = new ThreadedSocketExecuter(PS);
    SEB.start();
    server = SEB.createTCPServer("localhost", port);
    server.setSSLContext(sslCtx);
    server.setDoHandshake(true);
    server.setClientAcceptor((lc)->onClient(lc));
    server.start();
    this.sendBack.add(ByteBuffer.wrap(sendBack.getBytes()));
  }

  private void doSSLCrap() {
    try {
      KS = KeyStore.getInstance(KeyStore.getDefaultType());
      String filename = ClassLoader.getSystemClassLoader().getResource("keystore.jks").getFile();
      FileInputStream ksf = new FileInputStream(filename);
      KS.load(ksf, "password".toCharArray());
      kmf = KeyManagerFactory.getInstance("SunX509");
      kmf.init(KS, "password".toCharArray());
      sslCtx = SSLContext.getInstance("TLS");
      sslCtx.init(kmf.getKeyManagers(), myTMs, null);
    } catch(Exception e) {
      throw new RuntimeException(e);
    }

  }

  public void stop() {
    server.stop();
    server.close();
    SEB.stopIfRunning();
    PS.shutdownNow();
  }
  
  private void onClient(final Client c) {
    final HTTPRequestProcessor hrp = new HTTPRequestProcessor();
    hrp.addHTTPRequestCallback(new HTTPRequestCallback() {

      @Override
      public void headersFinished(HTTPRequest hreq) {
        c.write(hr.getByteBuffer());
        sendBack.begin();
        sendBack.begin();
        while(sendBack.remaining() > 0) {
          c.write(sendBack.pullBuffer(Math.min(500, sendBack.remaining())));
        }
        for(int i=0; i<kToSend; i++) {
          if(chunked) {
            c.write(ByteBuffer.wrap((Integer.toHexString(1024)+HTTPConstants.HTTP_NEWLINE_DELIMINATOR).getBytes()));
          }
          c.write(ByteBuffer.wrap(SEND_DATA));
          if(chunked) {
            c.write(ByteBuffer.wrap((HTTPConstants.HTTP_NEWLINE_DELIMINATOR).getBytes()));
          }
        }
        if(chunked) {
          c.write(ByteBuffer.wrap((Integer.toHexString(0)+HTTPConstants.HTTP_NEWLINE_DELIMINATOR).getBytes()));
        }

        sendBack.rollback();
        hrp.reset();
      }

      @Override
      public void bodyData(ByteBuffer bb) {
        // TODO Auto-generated method stub
        
      }

      @Override
      public void websocketData(WSFrame wsf, ByteBuffer bb) {
        // TODO Auto-generated method stub
        
      }

      @Override
      public void finished() {
        // TODO Auto-generated method stub
        
      }

      @Override
      public void hasError(Throwable t) {
        // TODO Auto-generated method stub
        
      }});
    clients.putIfAbsent(c, hrp);
    c.setReader((lc)->hrp.processData(lc.getRead()));
    c.addCloseListener((lc)->clients.remove(lc));
    
  }
}
