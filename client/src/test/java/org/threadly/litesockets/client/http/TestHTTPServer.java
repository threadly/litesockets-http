package org.threadly.litesockets.client.http;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.threadly.concurrent.PriorityScheduler;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.server.http.HTTPServer;
import org.threadly.litesockets.server.http.HTTPServer.BodyFuture;
import org.threadly.litesockets.server.http.HTTPServer.HTTPServerHandler;
import org.threadly.litesockets.server.http.HTTPServer.ResponseWriter;
import org.threadly.litesockets.utils.SSLUtils.FullTrustManager;
import org.threadly.litesockets.utils.TransactionalByteBuffers;

public class TestHTTPServer implements HTTPServerHandler {
  private final TrustManager[] myTMs = new TrustManager [] {new FullTrustManager() };
  private final ThreadedSocketExecuter SEB;
  private final PriorityScheduler PS;
  private KeyStore KS;
  private KeyManagerFactory kmf;
  private SSLContext sslCtx;
  private int port;
  private HTTPServer server;
  private TransactionalByteBuffers sendBack = new TransactionalByteBuffers();
  private boolean closeOnSend;
  private boolean doSSL;
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
    server = new HTTPServer(SEB, "localhost", port, sslCtx);
    server.addHandler(this);
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
    server.stopIfRunning();
    SEB.stopIfRunning();
    PS.shutdownNow();
  }

  @Override
  public void handle(HTTPRequest httpRequest, ResponseWriter responseWriter, BodyFuture bodyListener) {
    responseWriter.sendHTTPResponse(hr);
    sendBack.begin();
    while(sendBack.remaining() > 0) {
      responseWriter.writeBody(sendBack.pull(Math.min(500, sendBack.remaining())));
    }
    sendBack.rollback();
    responseWriter.done();
    if(closeOnSend) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      responseWriter.closeConnection();
    }
  }

}
