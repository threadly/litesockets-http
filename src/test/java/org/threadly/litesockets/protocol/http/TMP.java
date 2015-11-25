package org.threadly.litesockets.protocol.http;

import java.nio.ByteBuffer;

import org.junit.Test;
import org.threadly.litesockets.utils.MergedByteBuffers;
import org.threadly.protocols.http.request.HTTPRequest;
import org.threadly.protocols.http.request.HTTPRequestProcessor;
import org.threadly.protocols.http.request.HTTPRequestProcessor.HTTPRequestCallback;

public class TMP {

  @Test
  public void test() {
    String request = "GET /?test=test HTTP/1.1\r\nUser-Agent: curl/7.35.0\r\nHost: www.google.com\r\nAccept: */*\r\nContent-Length: 10\r\n\r\n";
    CallbackHandler ch = new CallbackHandler();
    HTTPRequestProcessor hrp = new HTTPRequestProcessor();
    hrp.addHTTPRequestCallback(ch);
    hrp.processData(request.getBytes());
    hrp.processData("123456789012\r\n\r\n".getBytes());
    hrp.connectionClosed();
    System.out.println(ch.headersFinished);
    System.out.println(ch.finished);
    System.out.println(ch.errors);
    System.out.println(ch.mbb.remaining());
  }
  
  public static class CallbackHandler implements HTTPRequestCallback {
    int headersFinished = 0;
    int finished = 0;
    int errors = 0;
    MergedByteBuffers mbb = new MergedByteBuffers(); 
    
    @Override
    public void headersFinished(HTTPRequest hr) {
      headersFinished++;
    }

    @Override
    public void bodyData(ByteBuffer bb) {
      mbb.add(bb);
    }

    @Override
    public void finished() {
      finished++;
    }

    @Override
    public void hasError(Throwable t) {
      errors++;
    }
    
  }
}
