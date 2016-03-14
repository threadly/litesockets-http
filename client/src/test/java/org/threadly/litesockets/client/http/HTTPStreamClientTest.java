package org.threadly.litesockets.client.http;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.future.FutureCallback;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.client.http.HTTPStreamClient.HTTPStreamReader;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.utils.MergedByteBuffers;
import org.threadly.test.concurrent.TestCondition;

public class HTTPStreamClientTest {
  static HTTPResponse RESPONSE_CHUNKED = new HTTPResponseBuilder().setHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING, "chunked").build();
  
  SocketExecuter SEI;
  PriorityScheduler PS;
  FakeHTTPStreamingServer fakeServer;

  @Before
  public void start() {
    PS = new PriorityScheduler(5);
    SEI = new ThreadedSocketExecuter(PS);
    SEI.start();
  }

  @After
  public void stop() {
    SEI.stop();
    PS.shutdownNow();
    if(fakeServer != null) {
      fakeServer.stop();
    }
  }
  
  @Test
  public void chunkRecvTest() throws IOException, InterruptedException, ExecutionException {
    final int port = TestUtils.findTCPPort();
    final int number = 300;
    fakeServer = new FakeHTTPStreamingServer(port, RESPONSE_CHUNKED, "", number, false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost/dl.php"));//.setChunked();
    final HTTPStreamClient hsc = new HTTPStreamClient(SEI, "localhost", port, 10000);
    final AtomicBoolean set = new AtomicBoolean(false);
    final MergedByteBuffers mbb = new MergedByteBuffers();
    hsc.setHTTPStreamReader(new HTTPStreamReader() {
      @Override
      public void handle(ByteBuffer bb) {
        mbb.add(bb);
      }});
    hsc.connect();

    final ListenableFuture<HTTPResponse> lf = hsc.writeRequest(hrb.build());
    lf.addCallback(new FutureCallback<HTTPResponse>() {

      @Override
      public void handleResult(HTTPResponse result) {
        set.set(true);
      }

      @Override
      public void handleFailure(Throwable t) {
        // TODO Auto-generated method stub
        
      }});
    
    new TestCondition(){
      @Override
      public boolean get() {
        return mbb.remaining() == (number*1024) && set.get();
      }
    }.blockTillTrue(5000);
    
    assertEquals("chunked", lf.get().getHeaders().getHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING));
    hsc.close();
  }
  
  @Test
  public void CLRecvTest() throws IOException, InterruptedException, ExecutionException {
    final int port = TestUtils.findTCPPort();
    final int number = 300;
    fakeServer = new FakeHTTPStreamingServer(port, HTTPClientTests.RESPONSE_HUGE, HTTPClientTests.LARGE_CONTENT, 0, false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost/dl.php"));//.setChunked();
    final HTTPStreamClient hsc = new HTTPStreamClient(SEI, "localhost", port, 10000);
    final AtomicBoolean set = new AtomicBoolean(false);
    final AtomicInteger count = new AtomicInteger(0);
    final MergedByteBuffers mbb = new MergedByteBuffers();
    hsc.setHTTPStreamReader(new HTTPStreamReader() {
      @Override
      public void handle(ByteBuffer bb) {
        mbb.add(bb);
      }});
    hsc.connect();
    
    final ListenableFuture<HTTPResponse> lf = hsc.writeRequest(hrb.build());
    
    lf.addCallback(new FutureCallback<HTTPResponse>() {

      @Override
      public void handleResult(HTTPResponse result) {
        set.set(true);
        count.addAndGet(Integer.parseInt(result.getHeaders().getHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH)));
      }

      @Override
      public void handleFailure(Throwable t) {
        // TODO Auto-generated method stub
        
      }});
    
    new TestCondition(){
      @Override
      public boolean get() {
        return mbb.remaining() == count.get() && set.get();
      }
    }.blockTillTrue(5000);
    hsc.close();
  }

}
