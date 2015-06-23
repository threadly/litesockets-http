package org.threadly.litesockets.protocol.http;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.future.FutureCallback;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.Client.Reader;
import org.threadly.litesockets.SocketExecuterInterface;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.protocol.http.structures.HTTPRequest.HTTPRequestBuilder;
import org.threadly.litesockets.protocol.http.structures.HTTPConstants;
import org.threadly.litesockets.protocol.http.structures.HTTPResponse;
import org.threadly.litesockets.utils.MergedByteBuffers;
import org.threadly.test.concurrent.TestCondition;

public class HTTPStreamClientTest {
  static String CHUNK_RESPONSE_TEMPLATE = "HTTP/1.1 200 OK\r\ntransfer-encoding: chunked\r\nContent-Type: text/html\r\n\r\n";
  
  SocketExecuterInterface SEI;
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
    fakeServer = new FakeHTTPStreamingServer(port, CHUNK_RESPONSE_TEMPLATE, number, false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost/dl.php"));//.setChunked();
    final HTTPStreamClient hsc = new HTTPStreamClient("localhost", port, 10000);
    final AtomicBoolean set = new AtomicBoolean(false);
    final MergedByteBuffers mbb = new MergedByteBuffers();
    hsc.setReader(new Reader() {
      @Override
      public void onRead(Client client) {
        MergedByteBuffers bb = client.getRead();
        mbb.add(bb);
      }});
    SEI.addClient(hsc);
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
        System.out.println(mbb.remaining()+":"+(number*1024));
        return mbb.remaining() == (number*1024) && set.get();
      }
    }.blockTillTrue(5000, 1000);
    
    assertEquals("chunked", lf.get().getHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING));
    hsc.close();
  }
  
  @Test
  public void CLRecvTest() throws IOException, InterruptedException, ExecutionException {
    final int port = TestUtils.findTCPPort();
    final int number = 300;
    fakeServer = new FakeHTTPStreamingServer(port, HTTPClientTests.RESPONSE_HUGE, 0, false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost/dl.php"));//.setChunked();
    final HTTPStreamClient hsc = new HTTPStreamClient("localhost", port, 10000);
    final AtomicBoolean set = new AtomicBoolean(false);
    final AtomicInteger count = new AtomicInteger(0);
    final MergedByteBuffers mbb = new MergedByteBuffers();
    hsc.setReader(new Reader() {
      @Override
      public void onRead(Client client) {
        MergedByteBuffers bb = client.getRead();
        mbb.add(bb);
      }});
    SEI.addClient(hsc);
    
    final ListenableFuture<HTTPResponse> lf = hsc.writeRequest(hrb.build());
    
    lf.addCallback(new FutureCallback<HTTPResponse>() {

      @Override
      public void handleResult(HTTPResponse result) {
        set.set(true);
        count.addAndGet(Integer.parseInt(result.getHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH)));
        //System.out.println();
      }

      @Override
      public void handleFailure(Throwable t) {
        // TODO Auto-generated method stub
        
      }});
    
    new TestCondition(){
      @Override
      public boolean get() {
        //System.out.println(mbb.remaining()+":"+(count.get()));
        return mbb.remaining() == count.get() && set.get();
      }
    }.blockTillTrue(5000, 100);
    
    //assertEquals(count.get(), lf.get().getHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH));
    hsc.close();
  }

}
