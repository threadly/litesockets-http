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
import org.threadly.litesockets.server.http.HTTPServer;
import org.threadly.litesockets.server.http.HTTPServer.BodyFuture;
import org.threadly.litesockets.server.http.HTTPServer.HTTPServerHandler;
import org.threadly.litesockets.server.http.HTTPServer.ResponseWriter;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.utils.SSLUtils.FullTrustManager;
import org.threadly.litesockets.buffers.TransactionalByteBuffers;

public class FakeHTTPStreamingServer implements HTTPServerHandler {
  public static byte[] SEND_DATA = new byte[1024];
  TrustManager[] myTMs = new TrustManager [] {new FullTrustManager() };
  KeyStore KS;
  KeyManagerFactory kmf;
  SSLContext sslCtx;
  PriorityScheduler PS;
  ThreadedSocketExecuter SEB;
  int port;
  HTTPServer server;
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
    server = new HTTPServer(SEB, "localhost", port, sslCtx);
    server.addHandler(this);
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
    server.stopIfRunning();
    SEB.stopIfRunning();
    PS.shutdownNow();
  }

  @Override
  public void handle(HTTPRequest httpRequest, ResponseWriter responseWriter, BodyFuture bodyListener) {
    responseWriter.sendHTTPResponse(hr);
    sendBack.begin();
    while(sendBack.remaining() > 0) {
      responseWriter.writeBody(sendBack.pullBuffer(Math.min(500, sendBack.remaining())));
    }
    for(int i=0; i<this.kToSend; i++) {
      if(chunked) {
        responseWriter.writeBody(ByteBuffer.wrap((Integer.toHexString(1024)+HTTPConstants.HTTP_NEWLINE_DELIMINATOR).getBytes()));
      }
      responseWriter.writeBody(ByteBuffer.wrap(SEND_DATA));
      if(chunked) {
        responseWriter.writeBody(ByteBuffer.wrap((HTTPConstants.HTTP_NEWLINE_DELIMINATOR).getBytes()));
      }
    }
    if(chunked) {
      responseWriter.writeBody(ByteBuffer.wrap((Integer.toHexString(0)+HTTPConstants.HTTP_NEWLINE_DELIMINATOR).getBytes()));
    }

    sendBack.rollback();

  }
}
