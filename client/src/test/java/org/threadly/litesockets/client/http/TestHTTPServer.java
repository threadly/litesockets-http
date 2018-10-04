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
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.TCPServer;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.buffers.TransactionalByteBuffers;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestProcessor;
import org.threadly.litesockets.protocols.http.request.HTTPRequestProcessor.HTTPRequestCallback;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.websocket.WSFrame;
import org.threadly.litesockets.utils.SSLUtils.FullTrustManager;

public class TestHTTPServer {
  private final TrustManager[] myTMs = new TrustManager [] {new FullTrustManager() };
  private final ThreadedSocketExecuter SEB;
  private final PriorityScheduler PS;
  private final ConcurrentHashMap<Client, HTTPRequestProcessor> clients = new ConcurrentHashMap<>();
  private KeyStore KS;
  private KeyManagerFactory kmf;
  private SSLContext sslCtx;
  private int port;
  private TCPServer server;
  private TransactionalByteBuffers sendBack = new TransactionalByteBuffers();
  private boolean closeOnSend;
  private HTTPResponse hr;

  public TestHTTPServer(int port, HTTPResponse hr, String sendBack, boolean doSSL, boolean closeOnSend) throws IOException {
    if(doSSL) {
      doSSLCrap();
    } 
    this.hr = hr;
    this.port = port;
    PS = new PriorityScheduler(5);
    SEB = new ThreadedSocketExecuter(PS);
    SEB.start();
    server = SEB.createTCPServer("localhost", this.port);
    server.setSSLContext(sslCtx);
    server.setDoHandshake(true);
    server.setClientAcceptor((lc)->onClient(lc));
    server.start();
    this.sendBack.add(ByteBuffer.wrap(sendBack.getBytes()));
    this.closeOnSend = closeOnSend;
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
        ListenableFuture<?> lf = c.write(hr.getByteBuffer());
        sendBack.begin();
        
        while(sendBack.remaining() > 0) {
          lf = c.write(sendBack.pullBuffer(Math.min(500, sendBack.remaining())));
        }
        sendBack.rollback();
        if(closeOnSend) {
          lf.addListener(()->c.close());
        }
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
