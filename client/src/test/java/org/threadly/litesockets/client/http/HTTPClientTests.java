package org.threadly.litesockets.client.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.future.FutureCallback;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.Server.ClientAcceptor;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.TCPServer;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.client.http.HTTPClient.HTTPResponseData;
import org.threadly.litesockets.protocols.http.request.ClientHTTPRequest;
import org.threadly.litesockets.protocols.http.request.ClientHTTPRequest.BodyConsumer;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPAddress;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;
import org.threadly.litesockets.utils.IOUtils;
import org.threadly.litesockets.utils.PortUtils;
import org.threadly.test.concurrent.AsyncVerifier;
import org.threadly.test.concurrent.TestCondition;
import org.threadly.util.ArrayIterator;
import org.threadly.util.Clock;

public class HTTPClientTests {
  static String CONTENT = "TEST123";
  static String XML_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><note><to>John</to><from>Jane</from><heading>XML Test</heading><body>This is a test, please discard</body></note>";
  static String LARGE_CONTENT;
  static HTTPResponse RESPONSE_CL;
  static HTTPResponse RESPONSE_CL_XML;
  static HTTPResponse RESPONSE_NO_CL;
  static HTTPResponse RESPONSE_HUGE;
  static HTTPResponse RESPONSE_HUGE_NOCL;
  static {
    StringBuilder sb = new StringBuilder();
    while(sb.length() < (HTTPRequestBuilder.MAX_HTTP_BUFFERED_RESPONSE*2)) {
      sb.append(CONTENT);
    }
    LARGE_CONTENT = sb.toString();
    RESPONSE_CL = new HTTPResponseBuilder().setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(CONTENT.length())).build();
    RESPONSE_CL_XML = new HTTPResponseBuilder().setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(XML_CONTENT.length())).build();
    RESPONSE_NO_CL = new HTTPResponseBuilder().replaceHTTPHeaders(new HTTPHeaders(new HashMap<String,String>())).build();
    RESPONSE_HUGE = new HTTPResponseBuilder().setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(LARGE_CONTENT.length())).build();
  }

  SocketExecuter SEI;
  PriorityScheduler PS;
  TestHTTPServer fakeServer;

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
  public void manyRequestsConcurrent() throws IOException, InterruptedException, TimeoutException {
    final int number = 500;
    final int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT.getBytes(), false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder().setPort(port);
    hrb.setHTTPAddress(new HTTPAddress("localhost", port, false), true);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start(); 

    AsyncVerifier av = new AsyncVerifier();
    PriorityScheduler CLIENT_PS = new PriorityScheduler(20);
    Runnable run = new Runnable() {
      @Override
      public void run() {
        ClientHTTPRequest chr = hrb.buildClientHTTPRequest();
        //final long start = Clock.accurateForwardProgressingMillis();

        final ListenableFuture<HTTPResponseData>  lf = httpClient.requestAsync(chr);
        lf.callback(new FutureCallback<HTTPResponseData>() {
          @Override
          public void handleResult(HTTPResponseData result) {
            //System.out.println("DELAY:"+(Clock.accurateForwardProgressingMillis()-start));
            av.assertEquals(CONTENT, result.getBodyAsString());
            av.signalComplete();
          }

          @Override
          public void handleFailure(Throwable t) {
            av.fail(t);
          }});
      }};

      for(int i=0; i<number; i++) {
        CLIENT_PS.execute(run);
      }
      
      av.waitForTest(10_000, number);
      
      httpClient.stop();
  }
  
  @Test
  public void manyRequestsConcurrentJavaExecutor() throws IOException, InterruptedException, TimeoutException {
    final int number = 500;
    final int port = PortUtils.findTCPPort();
    final ThreadedSocketExecuter TSE = new ThreadedSocketExecuter(PS);
    TSE.start();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT.getBytes(), false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setHTTPAddress(new HTTPAddress("localhost", port, false), true);
    final HTTPClient httpClient = new HTTPClient(HTTPClient.DEFAULT_CONCURRENT, TSE);
    httpClient.start();

    AsyncVerifier av = new AsyncVerifier();
    PriorityScheduler CLIENT_PS = new PriorityScheduler(20);
    Runnable run = new Runnable() {
      @Override
      public void run() {
        ClientHTTPRequest chr = hrb.buildClientHTTPRequest();
        //final long start = Clock.accurateForwardProgressingMillis();

        final ListenableFuture<HTTPResponseData>  lf = httpClient.requestAsync(chr);
        lf.callback(new FutureCallback<HTTPResponseData>() {
          @Override
          public void handleResult(HTTPResponseData result) {
            //System.out.println("DELAY:"+(Clock.accurateForwardProgressingMillis()-start));
            av.assertEquals(CONTENT, result.getBodyAsString());
            av.signalComplete();
          }

          @Override
          public void handleFailure(Throwable t) {
            av.fail(t);
          }});
      }};

      for(int i=0; i<number; i++) {
        CLIENT_PS.execute(run);
      }
      
      av.waitForTest(10_000, number);
      
      httpClient.stop();
      TSE.stop();
  }

  @Test
  public void manyRequestsConcurrentOnPool() throws IOException, InterruptedException, TimeoutException {
    final int number = 500;
    final int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT.getBytes(), false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setHTTPAddress(new HTTPAddress("localhost", port, false), true);
    final ThreadedSocketExecuter TSE = new ThreadedSocketExecuter(PS);
    TSE.start();
    final HTTPClient httpClient = new HTTPClient(200, TSE);
    httpClient.start();

    AsyncVerifier av = new AsyncVerifier();
    PriorityScheduler CLIENT_PS = new PriorityScheduler(20);
    Runnable run = new Runnable() {
      @Override
      public void run() {
        ClientHTTPRequest chr = hrb.buildClientHTTPRequest();
        //final long start = Clock.accurateForwardProgressingMillis();

        final ListenableFuture<HTTPResponseData>  lf = httpClient.requestAsync(chr);
        lf.callback(new FutureCallback<HTTPResponseData>() {
          @Override
          public void handleResult(HTTPResponseData result) {
            //System.out.println("DELAY:"+(Clock.accurateForwardProgressingMillis()-start));
            av.assertEquals(CONTENT, result.getBodyAsString());
            av.signalComplete();
          }

          @Override
          public void handleFailure(Throwable t) {
            av.fail(t);
          }});
      }};

      for(int i=0; i<number; i++) {
        CLIENT_PS.execute(run);
      }
      
      av.waitForTest(10_000, number);
      
      httpClient.stop();
      TSE.stop();
  }

  @Test
  public void blockingRequest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT.getBytes(), false, true, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setHTTPAddress(new HTTPAddress("localhost", port, false), true);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    assertEquals(CONTENT, httpClient.request(hrb.buildClientHTTPRequest()).getBodyAsString());
  }

  @Test
  public void noContentLengthNoBody() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_NO_CL, "".getBytes(), false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setHTTPAddress(new HTTPAddress("localhost", port, false), true);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    HTTPResponseData hrs = httpClient.request(hrb.buildClientHTTPRequest());
    assertEquals("", hrs.getBodyAsString());
  }

  @Test
  public void noContentLengthWithBody() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_NO_CL, CONTENT.getBytes(), false, true, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    HTTPResponseData hrs = httpClient.request(hrb.buildClientHTTPRequest());
    //System.out.println(hrs.getResponse());
    assertEquals(CONTENT, hrs.getBodyAsString());
  }

  @Test
  public void streamedBodyRequest() throws IOException, HTTPParsingException, InterruptedException, ExecutionException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT.getBytes(), false, true, true);
    ByteBuffer write1 = ByteBuffer.allocate(100);
    ByteBuffer write2 = ByteBuffer.allocate(100);
    SettableListenableFuture<ByteBuffer> write1SLF = new SettableListenableFuture<>();
    SettableListenableFuture<ByteBuffer> write2SLF = new SettableListenableFuture<>();
    @SuppressWarnings({"unchecked", "rawtypes"})
    Iterator<ListenableFuture<ByteBuffer>> writeIt = 
        ArrayIterator.makeIterator(new ListenableFuture[] { 
            write1SLF, write2SLF, FutureUtils.immediateResultFuture(null) });
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port))
        .setStreamedBody(write1.remaining() + write2.remaining(), writeIt::next);
    
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    ListenableFuture<HTTPResponseData> lf = httpClient.requestAsync(hrb.buildClientHTTPRequest());
    Thread.sleep(100);
    assertFalse(lf.isDone());
    write1SLF.setResult(write1);
    Thread.sleep(100);
    assertFalse(lf.isDone());
    write2SLF.setResult(write2);
    
    assertEquals(CONTENT, lf.get().getBodyAsString());
  }

  @Test
  public void streamedBodyResponse() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_HUGE, LARGE_CONTENT.getBytes(), false, false, false);
    AtomicInteger readContentSize = new AtomicInteger(0);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port))
        .setBodyConsumer(new BodyConsumer() {
          @Override
          public void accept(ByteBuffer bb) throws HTTPParsingException {
            readContentSize.addAndGet(bb.remaining());
          }

          @Override
          public MergedByteBuffers finishBody() {
            return null;
          }
        });
    
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    MergedByteBuffers emptyResponseBody = httpClient.request(hrb.buildClientHTTPRequest()).getBody();
    
    assertEquals(0, emptyResponseBody.remaining());
    assertEquals(LARGE_CONTENT.length(), readContentSize.get());
  }

  @Test
  public void chunkedBodyResponse() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    MergedByteBuffers chunkBuffers = HTTPUtils.wrapInChunk(ByteBuffer.wrap(CONTENT.getBytes()));
    byte[] chunkBytes = new byte[chunkBuffers.remaining()];
    chunkBuffers.get(chunkBytes);
    HTTPResponseBuilder responseBuilder = new HTTPResponseBuilder();
    responseBuilder.setHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING, "chunked");
    fakeServer = new TestHTTPServer(port, responseBuilder.build(), chunkBytes, false, true, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    HTTPResponseData hrs = httpClient.request(hrb.buildClientHTTPRequest());
    //System.out.println(hrs.getResponse());
    assertEquals(CONTENT, hrs.getBodyAsString());
  }

  @Test
  public void contentLengthOnHeadRequest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, "".getBytes(), false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setRequestMethod("HEAD");
    
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    HTTPResponseData hrs = httpClient.request(hrb.buildClientHTTPRequest());
    //System.out.println(hrs.getResponse());
    assertEquals(CONTENT.length(), hrs.getContentLength());
    assertEquals("", hrs.getBodyAsString());
  }

  @Test
  public void closeBeforeLength() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_HUGE, CONTENT.getBytes(), false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setBody(IOUtils.EMPTY_BYTEBUFFER);
    hrb.setTimeout(10000, TimeUnit.MILLISECONDS);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    try{
      httpClient.request(hrb.buildClientHTTPRequest());
      fail();
    } catch(HTTPParsingException e) {
      assertTrue(e.getMessage().startsWith("Body not complete"));
    }
  }
  
  @Test
  public void timeoutRequest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    TCPServer server = SEI.createTCPServer("localhost", port);
    server.start();
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setBody(IOUtils.EMPTY_BYTEBUFFER);
    hrb.setTimeout(200, TimeUnit.MILLISECONDS);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    try{
      httpClient.request(hrb.buildClientHTTPRequest());
      fail();
    } catch(CancellationException e) {
      assertEquals("Request timed out at point: SendingRequest", e.getMessage());
      // below conditions may be slightly async due to future getting a result before listeners are invoked
      new TestCondition(() -> httpClient.getRequestQueueSize() == 0).blockTillTrue(1_000);
      new TestCondition(() -> httpClient.getInProgressCount() == 0).blockTillTrue(1_000);
    }
  }
  
  @Test
  public void timeoutQueuedRequest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    TCPServer server = SEI.createTCPServer("localhost", port);
    server.start();
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setBody(IOUtils.EMPTY_BYTEBUFFER);
    hrb.setTimeout(200, TimeUnit.MILLISECONDS);
    final HTTPClient httpClient = new HTTPClient() {
      @Override
      protected void processQueue() {
        // queue is never processed
      }
    };
    httpClient.start();
    try{
      httpClient.request(hrb.buildClientHTTPRequest());
      fail();
    } catch(CancellationException e) {
      assertEquals("Request timed out at point: Queued", e.getMessage());
      // below conditions may be slightly async due to future getting a result before listeners are invoked
      new TestCondition(() -> httpClient.getRequestQueueSize() == 0).blockTillTrue(1_000);
      new TestCondition(() -> httpClient.getInProgressCount() == 0).blockTillTrue(1_000);
    }
  }
  
  @Test
  public void queueLimitTest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    TCPServer server = SEI.createTCPServer("localhost", port);
    server.start();
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setBody(IOUtils.EMPTY_BYTEBUFFER);
    final HTTPClient httpClient = new HTTPClient(1, 1) {
      @Override
      protected void processQueue() {
        // queue is never processed so we know it's queued
      }
    };
    httpClient.start();
    httpClient.requestAsync(hrb.buildClientHTTPRequest()); // first request should queue fine
    try{
      httpClient.request(hrb.buildClientHTTPRequest());
      fail();
    } catch(RejectedExecutionException e) {
      assertEquals(1, httpClient.getRequestQueueSize());
      assertEquals("Request queue full", e.getMessage());
    }
  }

  @Test
  public void expireRequest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, "".getBytes(), false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setBody(IOUtils.EMPTY_BYTEBUFFER);
    hrb.setTimeout(30, TimeUnit.MILLISECONDS);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    long start = Clock.accurateForwardProgressingMillis();
    try {
      httpClient.request(hrb.buildClientHTTPRequest());
      fail();
    } catch(CancellationException e) {
      assertEquals("Request timed out at point: ReadingResponseBody", e.getMessage());
    }
    assertTrue((Clock.accurateForwardProgressingMillis() - start) > 30);
  }

  @Test
  public void sslRequest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT.getBytes(), true, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("https://localhost:"+port));
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    assertEquals(CONTENT, httpClient.request(hrb.buildClientHTTPRequest()).getBodyAsString());
  }

  @Test
  public void tooLargeRequest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_HUGE, LARGE_CONTENT.getBytes(), false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    try {
      httpClient.request(hrb.buildClientHTTPRequest());
      fail();
    } catch(HTTPParsingException e) {
      assertEquals("Response Body to large!", e.getMessage());
    }
  }

  @Test
  public void tooLargeRequestNoContentLength() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_NO_CL, LARGE_CONTENT.getBytes(), false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    try {
      httpClient.request(hrb.buildClientHTTPRequest());
      fail();
    } catch(HTTPParsingException e) {
      assertEquals("Response Body to large!", e.getMessage());
    }
  }

  //  @Test
  //  public void blockingRequestERROR() throws IOException, InterruptedException, ExecutionException, TimeoutException {
  //    int port = TestUtils.findTCPPort();
  //    fakeServer = new TestHTTPServer(port, RESPONSE_ERROR_1, false, false);
  //    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
  //    final HTTPClient httpClient = new HTTPClient();
  //    final ListenableFuture<HTTPResponseData> lf = httpClient.requestAsync(new HTTPAddress("localhost", port, false), hrb.build());
  //    final AtomicBoolean failed = new AtomicBoolean(false);
  //    lf.addCallback(new FutureCallback<HTTPResponseData>() {
  //
  //      @Override
  //      public void handleResult(HTTPResponseData result) {
  //        fail();
  //      }
  //
  //      @Override
  //      public void handleFailure(Throwable t) {
  //        failed.set(false);
  //      }});
  //
  //    try {
  //      lf.get(5000, TimeUnit.MILLISECONDS);
  //      fail();
  //    } catch(ExecutionException e) {
  //
  //    }
  //    assertFalse(failed.get());
  //
  //  }

  @Test
  public void timeOut() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    int port = PortUtils.findTCPPort();
    TCPServer server =  SEI.createTCPServer("localhost", port);
    server.setClientAcceptor(new ClientAcceptor() {
      @Override
      public void accept(Client c) {
        //System.out.println("new Client!");
      }});
    server.start();
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setTimeout(500, TimeUnit.MILLISECONDS);
    hrb.setBody(IOUtils.EMPTY_BYTEBUFFER);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();

    ClientHTTPRequest chr = hrb.buildClientHTTPRequest();

    long start = System.currentTimeMillis();
    try{
      httpClient.request(chr);
    } catch(Exception e) {
      assertTrue(System.currentTimeMillis() - start < 700);
      return;
    }
    fail();
  }


  @Test
  public void urlRequest() throws HTTPParsingException, IOException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT.getBytes(), false, false, true);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    HTTPResponseData hrd = httpClient.request(new URL("http://localhost:"+port));
    assertEquals(CONTENT, hrd.getBodyAsString());
  }
}
