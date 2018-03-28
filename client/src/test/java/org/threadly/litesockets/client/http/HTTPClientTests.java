package org.threadly.litesockets.client.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.future.FutureCallback;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.litesockets.Client;
import org.threadly.litesockets.Server.ClientAcceptor;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.TCPServer;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.buffers.ReuseableMergedByteBuffers;
import org.threadly.litesockets.client.http.HTTPClient.HTTPResponseData;
import org.threadly.litesockets.client.http.HTTPStreamClient.HTTPStreamReader;
import org.threadly.litesockets.protocols.http.request.ClientHTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPAddress;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;
import org.threadly.litesockets.utils.IOUtils;
import org.threadly.litesockets.utils.PortUtils;
import org.threadly.test.concurrent.TestCondition;
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
    while(sb.length() < (HTTPClient.MAX_HTTP_RESPONSE*2)) {
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
  public void manyRequestsConcurrent() throws IOException {
    final int number = 500;
    final int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT, false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder().setPort(port);
    hrb.setHTTPAddress(new HTTPAddress("localhost", port, false));
    final HTTPClient httpClient = new HTTPClient();
    final AtomicInteger count = new AtomicInteger(0);
    httpClient.start(); 

    PriorityScheduler CLIENT_PS = new PriorityScheduler(20);
    Runnable run = new Runnable() {
      @Override
      public void run() {
        ClientHTTPRequest chr = hrb.buildClientHTTPRequest();
        //final long start = Clock.accurateForwardProgressingMillis();
        final ListenableFuture<HTTPResponseData>  lf = httpClient.requestAsync(chr);
        lf.addCallback(new FutureCallback<HTTPResponseData>() {
          @Override
          public void handleResult(HTTPResponseData result) {
            //System.out.println("DELAY:"+(Clock.accurateForwardProgressingMillis()-start));
            assertEquals("TEST123", result.getBodyAsString());
            count.incrementAndGet();
          }

          @Override
          public void handleFailure(Throwable t) {
            System.out.println("***********************ERR*******************");
            try {
              System.out.println(lf.get().getBodyAsString());
            } catch (InterruptedException e) {
              e.printStackTrace();
            } catch (ExecutionException e) {
              e.printStackTrace();
            }
            System.out.println("***********************ERR*******************");
            fail();
          }});


      }};

      for(int i=0; i<number; i++) {
        CLIENT_PS.execute(run);
      }
      new TestCondition(){
        @Override
        public boolean get() {
          return count.get() == number;
        }
      }.blockTillTrue(10000);
      httpClient.stop();
  }
  
  @Test
  public void manyRequestsConcurrentJavaExecutor() throws IOException, InterruptedException {
    final int number = 500;
    final int port = PortUtils.findTCPPort();
    final ThreadedSocketExecuter TSE = new ThreadedSocketExecuter(PS);
    TSE.start();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT, false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setHTTPAddress(new HTTPAddress("localhost", port, false));
    final HTTPClient httpClient = new HTTPClient(HTTPClient.DEFAULT_CONCURRENT, HTTPClient.MAX_HTTP_RESPONSE, TSE);
    httpClient.start();
    final AtomicInteger count = new AtomicInteger(0);

    PriorityScheduler CLIENT_PS = new PriorityScheduler(200);
    Runnable run = new Runnable() {
      @Override
      public void run() {
        ClientHTTPRequest chr = hrb.buildClientHTTPRequest();
        //final long start = Clock.accurateForwardProgressingMillis();
        final ListenableFuture<HTTPResponseData>  lf = httpClient.requestAsync(chr);
        lf.addCallback(new FutureCallback<HTTPResponseData>() {
          @Override
          public void handleResult(HTTPResponseData result) {
            //System.out.println("DELAY:"+(Clock.accurateForwardProgressingMillis()-start));
            assertEquals("TEST123", result.getBodyAsString());
            count.incrementAndGet();
          }

          @Override
          public void handleFailure(Throwable t) {
            System.out.println("***********************ERR*******************");
            try {
              System.out.println(lf.get().getBodyAsString());
            }  catch (InterruptedException e) {
              e.printStackTrace();
            } catch (ExecutionException e) {
              e.printStackTrace();
            }
            System.out.println("***********************ERR*******************");
            fail();
          }});


      }};

      for(int i=0; i<number; i++) {
        CLIENT_PS.execute(run);
      }
      Thread.sleep(1000);
      System.out.println(count.get());
      new TestCondition(){
        @Override
        public boolean get() {
          return count.get() == number;
        }
      }.blockTillTrue(10000);
      httpClient.stop();
      TSE.stop();
  }


  @Test
  public void manyRequestsConcurrentOnPool() throws IOException {
    final int number = 500;
    final int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT, false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setHTTPAddress(new HTTPAddress("localhost", port, false));
    final ThreadedSocketExecuter TSE = new ThreadedSocketExecuter(PS);
    TSE.start();
    final HTTPClient httpClient = new HTTPClient(200, HTTPClient.MAX_HTTP_RESPONSE, TSE);
    httpClient.start();
    final AtomicInteger count = new AtomicInteger(0);

    PriorityScheduler CLIENT_PS = new PriorityScheduler(200);
    Runnable run = new Runnable() {
      @Override
      public void run() {
        ClientHTTPRequest chr = hrb.buildClientHTTPRequest();
        //final long start = Clock.accurateForwardProgressingMillis();

        final ListenableFuture<HTTPResponseData>  lf = httpClient.requestAsync(chr);
        lf.addCallback(new FutureCallback<HTTPResponseData>() {
          @Override
          public void handleResult(HTTPResponseData result) {
            //System.out.println("DELAY:"+(Clock.accurateForwardProgressingMillis()-start));
            assertEquals("TEST123", result.getBodyAsString());
            count.incrementAndGet();
          }

          @Override
          public void handleFailure(Throwable t) {
            System.out.println("***********************ERR*******************");
            try {
              System.out.println(lf.get().getBodyAsString());
            }  catch (InterruptedException e) {
              e.printStackTrace();
            } catch (ExecutionException e) {
              e.printStackTrace();
            }
            System.out.println("***********************ERR*******************");
            fail();
          }});


      }};

      for(int i=0; i<number; i++) {
        CLIENT_PS.execute(run);
      }
      new TestCondition(){
        @Override
        public boolean get() {
          return count.get() == number;
        }
      }.blockTillTrue(10000);
      httpClient.stop();
      TSE.stop();
  }

  @Test
  public void blockingRequest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT, false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setHTTPAddress(new HTTPAddress("localhost", port, false));
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    assertEquals("TEST123", httpClient.request(hrb.buildClientHTTPRequest()).getBodyAsString());
  }

  @Test
  public void noContentLengthNoBody() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_NO_CL, "", false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setHTTPAddress(new HTTPAddress("localhost", port, false));
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    HTTPResponseData hrs = httpClient.request(hrb.buildClientHTTPRequest());
    assertEquals("", hrs.getBodyAsString());
  }

  @Test
  public void noContentLengthWithBody() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_NO_CL, CONTENT, false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    HTTPResponseData hrs = httpClient.request(hrb.buildClientHTTPRequest());
    System.out.println(hrs.getResponse());
    assertEquals("TEST123", hrs.getBodyAsString());
  }

  @Test
  public void closeBeforeLength() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_HUGE, CONTENT, false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setBody(IOUtils.EMPTY_BYTEBUFFER);
    hrb.setTimeout(TimeUnit.MILLISECONDS, 10000);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    try{
      httpClient.request(hrb.buildClientHTTPRequest());
      fail();
    } catch(HTTPParsingException e) {
      e.printStackTrace();
      assertEquals("Did not get complete body!", e.getMessage());
    }
  }
  
  @Test
  public void timeoutRequest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    TCPServer server = SEI.createTCPServer("localhost", port);
    server.start();
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setBody(IOUtils.EMPTY_BYTEBUFFER);
    hrb.setTimeout(TimeUnit.MILLISECONDS, 500);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    try{
      httpClient.request(hrb.buildClientHTTPRequest());
      fail();
    } catch(HTTPParsingException e) {
      assertEquals("HTTP Timeout!", e.getMessage());
    }
  }

  @Test
  public void expireRequest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, "", false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setBody(IOUtils.EMPTY_BYTEBUFFER);
    hrb.setTimeout(TimeUnit.MILLISECONDS, 30);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    long start = Clock.accurateForwardProgressingMillis();
    try {
      httpClient.request(hrb.buildClientHTTPRequest());
      fail();
    } catch(HTTPParsingException hp) {

    }
    assertTrue((Clock.accurateForwardProgressingMillis() - start) > 30);
  }

  @Test
  public void sslRequest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT, true, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("https://localhost:"+port));
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    assertEquals("TEST123", httpClient.request(hrb.buildClientHTTPRequest()).getBodyAsString());
  }

  @Test
  public void toLargeRequest() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_HUGE, LARGE_CONTENT, false, false);
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
  public void toLargeRequestNoContentLength() throws IOException, HTTPParsingException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_NO_CL, LARGE_CONTENT, false, true);
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
        System.out.println("new Client!");
      }});
    server.start();
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setTimeout(TimeUnit.MILLISECONDS, 500);
    hrb.setBody(IOUtils.EMPTY_BYTEBUFFER);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();

    ClientHTTPRequest chr = hrb.buildClientHTTPRequest();

    long start = System.currentTimeMillis();
    try{
      httpClient.request(chr);
    } catch(Exception e) {
      System.out.println(System.currentTimeMillis() - start);
      assertTrue(System.currentTimeMillis() - start < 700);
      return;
    }
    fail();
  }


  @Test
  public void urlRequest() throws HTTPParsingException, IOException {
    int port = PortUtils.findTCPPort();
    fakeServer = new TestHTTPServer(port, RESPONSE_CL, CONTENT, false, false);
    final HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    HTTPResponseData hrd = httpClient.request(new URL("http://localhost:"+port));
    assertEquals(CONTENT, hrd.getBodyAsString());
  }
}
