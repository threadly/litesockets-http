package org.threadly.litesockets.protocol.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.future.FutureCallback;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.litesockets.SocketExecuterInterface;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.protocol.http.structures.HTTPRequest;
import org.threadly.litesockets.protocol.http.structures.HTTPRequest.HTTPRequestBuilder;
import org.threadly.litesockets.protocol.http.structures.HTTPResponse;
import org.threadly.test.concurrent.TestCondition;
import org.threadly.util.Clock;

public class HTTPClientTests {
  static String CONTENT = "TEST123";
  static String RESPONSE_TEMPLATE = "HTTP/1.1 200 OK\r\nContent-Length: {LENGTH}\r\nContent-Type: text/html\r\n\r\n{BODY}";
  static String RESPONSE1 = RESPONSE_TEMPLATE.replace("{BODY}", CONTENT).replace("{LENGTH}", Integer.toString(CONTENT.length()));
  static String RESPONSE_PARTIAL = RESPONSE_TEMPLATE.replace("{BODY}", CONTENT).replace("{LENGTH}", Integer.toString(CONTENT.length()*3));
  static String RESPONSE_ERROR_1 = "\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n";
  static String RESPONSE_HUGE;
  static String RESPONSE_HUGE_NOCL;
  static String RESPONSE_NOCL_NOBODY = RESPONSE_TEMPLATE.replace("Content-Length: {LENGTH}\r\n", "").replace("{BODY}", "");
  static String RESPONSE_NOCL_WITH = RESPONSE_NOCL_NOBODY+CONTENT;
  static {
    StringBuilder sb = new StringBuilder();
    while(sb.length() < (HTTPClient.MAX_HTTP_RESPONSE*2)) {
      sb.append(CONTENT);
    }
    RESPONSE_HUGE = RESPONSE_TEMPLATE.replace("{LENGTH}", Integer.toString(sb.length())).replace("{BODY}", sb.toString());
    RESPONSE_HUGE_NOCL = RESPONSE_TEMPLATE.replace("Content-Length: {LENGTH}\r\n", "").replace("{BODY}", sb.toString());
  }

  SocketExecuterInterface SEI;
  PriorityScheduler PS;
  FakeHTTPServer fakeServer;

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
    int port = TestUtils.findTCPPort();
    fakeServer = new FakeHTTPServer(port, RESPONSE1, false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port)).setReadTimeout(8000);
    final HTTPClient httpClient = new HTTPClient();
    final AtomicInteger count = new AtomicInteger(0);

    PriorityScheduler CLIENT_PS = new PriorityScheduler(200);
    Runnable run = new Runnable() {
      @Override
      public void run() {
        HTTPRequest hr = hrb.build();
        //final long start = Clock.accurateForwardProgressingMillis();
        final ListenableFuture<HTTPResponse>  lf = httpClient.requestAsync(hr);
        lf.addCallback(new FutureCallback<HTTPResponse>() {
          @Override
          public void handleResult(HTTPResponse result) {
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
  public void manyRequestsConcurrentJavaExecutor() throws IOException {
    final int number = 500;
    int port = TestUtils.findTCPPort();
    final ThreadedSocketExecuter TSE = new ThreadedSocketExecuter(PS);
    TSE.start();
    fakeServer = new FakeHTTPServer(port, RESPONSE1, false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port)).setReadTimeout(8000);
    final HTTPClient httpClient = new HTTPClient(HTTPClient.DEFAULT_CONCURRENT, HTTPClient.MAX_HTTP_RESPONSE, TSE);
    final AtomicInteger count = new AtomicInteger(0);

    PriorityScheduler CLIENT_PS = new PriorityScheduler(200);
    Runnable run = new Runnable() {
      @Override
      public void run() {
        HTTPRequest hr = hrb.build();
        //final long start = Clock.accurateForwardProgressingMillis();
        final ListenableFuture<HTTPResponse>  lf = httpClient.requestAsync(hr);
        lf.addCallback(new FutureCallback<HTTPResponse>() {
          @Override
          public void handleResult(HTTPResponse result) {
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
  public void manyRequestsConcurrentOnPool() throws IOException {
    final int number = 500;
    int port = TestUtils.findTCPPort();
    fakeServer = new FakeHTTPServer(port, RESPONSE1, false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port)).setReadTimeout(8000);
    final ThreadedSocketExecuter TSE = new ThreadedSocketExecuter(PS);
    TSE.start();
    final HTTPClient httpClient = new HTTPClient(200, HTTPClient.MAX_HTTP_RESPONSE, TSE);
    final AtomicInteger count = new AtomicInteger(0);

    PriorityScheduler CLIENT_PS = new PriorityScheduler(200);
    Runnable run = new Runnable() {
      @Override
      public void run() {
        HTTPRequest hr = hrb.build();
        //final long start = Clock.accurateForwardProgressingMillis();

        final ListenableFuture<HTTPResponse>  lf = httpClient.requestAsync(hr);
        lf.addCallback(new FutureCallback<HTTPResponse>() {
          @Override
          public void handleResult(HTTPResponse result) {
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
  public void blockingRequest() throws IOException {
    int port = TestUtils.findTCPPort();
    fakeServer = new FakeHTTPServer(port, RESPONSE1, false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port)).setReadTimeout(8000);
    final HTTPClient httpClient = new HTTPClient();
    assertEquals("TEST123", httpClient.request(hrb.build()).getBodyAsString());
  }

  @Test
  public void noContentLengthNoBody() throws IOException {
    int port = TestUtils.findTCPPort();
    fakeServer = new FakeHTTPServer(port, RESPONSE_NOCL_NOBODY, false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port)).setReadTimeout(8000);
    final HTTPClient httpClient = new HTTPClient();
    HTTPResponse hrs = httpClient.request(hrb.build());
    assertEquals("", hrs.getBodyAsString());
  }

  @Test
  public void noContentLengthWithBody() throws IOException {
    int port = TestUtils.findTCPPort();
    fakeServer = new FakeHTTPServer(port, RESPONSE_NOCL_WITH, false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port)).setReadTimeout(8000);
    final HTTPClient httpClient = new HTTPClient();
    HTTPResponse hrs = httpClient.request(hrb.build());
    assertEquals("TEST123", hrs.getBodyAsString());
  }

  @Test
  public void closeBeforeLength() throws IOException {
    int port = TestUtils.findTCPPort();
    fakeServer = new FakeHTTPServer(port, RESPONSE_PARTIAL, false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port)).setReadTimeout(8000);
    final HTTPClient httpClient = new HTTPClient();
    HTTPResponse hrs = httpClient.request(hrb.build());
    assertTrue(hrs.hasError());
    assertTrue(hrs.getError().getMessage().startsWith("Error Closed before Response was Done!"));
    //System.out.println(hrs.getBodyAsString());
    //System.out.println(hrs.getError());
    //assertEquals("TEST123", httpClient.request(hrb.build()).getBodyAsString());
  }

  @Test
  public void expireRequest() throws IOException {
    int port = TestUtils.findTCPPort();
    fakeServer = new FakeHTTPServer(port, "", false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port)).setReadTimeout(300);
    final HTTPClient httpClient = new HTTPClient();
    long start = Clock.accurateForwardProgressingMillis();
    HTTPResponse hrs = httpClient.request(hrb.build());
    assertTrue((Clock.accurateForwardProgressingMillis() - start) > 300);
    assertTrue(hrs.hasError());
    assertTrue(hrs.getError().getMessage().startsWith("Request timedout"));
  }

  @Test
  public void sslRequest() throws IOException {
    int port = TestUtils.findTCPPort();
    fakeServer = new FakeHTTPServer(port, RESPONSE1, true, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("https://localhost:"+port)).setReadTimeout(8000);
    final HTTPClient httpClient = new HTTPClient();
    assertEquals("TEST123", httpClient.request(hrb.build()).getBodyAsString());
  }

  @Test
  public void toLargeRequest() throws IOException {
    int port = TestUtils.findTCPPort();
    fakeServer = new FakeHTTPServer(port, RESPONSE_HUGE, false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port)).setReadTimeout(8000);
    final HTTPClient httpClient = new HTTPClient();
    final HTTPResponse hrs = httpClient.request(hrb.build());
    //System.out.println(hrs.getBodyAsString());
    assertTrue(hrs.hasError());
    assertTrue(hrs.getError().getMessage().startsWith("HTTPResponse was to large"));
  }

  @Test
  public void toLargeRequestNoContentLength() throws IOException {
    int port = TestUtils.findTCPPort();
    fakeServer = new FakeHTTPServer(port, RESPONSE_HUGE, false, true);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port)).setReadTimeout(8000);
    final HTTPClient httpClient = new HTTPClient();
    final HTTPResponse hrs = httpClient.request(hrb.build());
    //System.out.println(hrs.getBodyAsString());
    assertTrue(hrs.hasError());
    assertTrue(hrs.getError().getMessage().startsWith("HTTPResponse was to large"));
  }

  @Test
  public void blockingRequestERROR() throws IOException {
    int port = TestUtils.findTCPPort();
    fakeServer = new FakeHTTPServer(port, RESPONSE_ERROR_1, false, false);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port)).setReadTimeout(8000);
    final HTTPClient httpClient = new HTTPClient();
    final ListenableFuture<HTTPResponse> lf = httpClient.requestAsync(hrb.build());
    final AtomicBoolean failed = new AtomicBoolean(false);
    lf.addCallback(new FutureCallback<HTTPResponse>() {

      @Override
      public void handleResult(HTTPResponse result) {
        //System.out.println(result.getError());
        if(result.hasError()) {
          assertTrue(result.getError().getMessage().startsWith("Error while parsing HTTP"));
        } else {
          failed.set(true);
          fail();
        }
      }

      @Override
      public void handleFailure(Throwable t) {
        failed.set(true);
        fail();
      }});

    new TestCondition(){
      @Override
      public boolean get() {
        return lf.isDone();
      }
    }.blockTillTrue(5000);
    assertFalse(failed.get());

  }


  /*
  @Test
  public void timeOut() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    int port = Utils.findTCPPort();
    TCPServer server = new TCPServer("localhost", port);
    server.setClientAcceptor(new ClientAcceptor() {
      @Override
      public void accept(Client c) {
        System.out.println("new Client!");
      }});
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    hrb.setReadTimeout(100);
    final HTTPClient httpClient = new HTTPClient("localhost", port, false);
    SEB.addClient(httpClient);
    SEB.addServer(server);
    HTTPRequest hr = hrb.build();

    ListenableFuture<HTTPResponse> lf = httpClient.sendRequest(hr);
    long start = System.currentTimeMillis();
    try{
      lf.get();
    } catch(ExecutionException e) {
      System.out.println(System.currentTimeMillis() - start);
      assertTrue(System.currentTimeMillis() - start < 300);
      return;
    }
    //We should not get here
    assertTrue(false);
  }

  @Test
  public void onClose() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    int port = Utils.findTCPPort();
    TCPServer server = new TCPServer("localhost", port);
    server.setClientAcceptor(new ClientAcceptor() {
      @Override
      public void accept(final Client c) {
        System.out.println("new Client!");
        TCPClient tc = (TCPClient)c; 
        tc.setReader(new Reader() {
          @Override
          public void onRead(Client client) {
            client.getRead();
            System.out.println("ClientGot Read!");
          }});
        SEB.addClient(c);
        PS.schedule(new Runnable() {
          @Override
          public void run() {
            c.close();
            System.out.println("ClientClosed!");
          }}, 200);
      }});
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    final HTTPClient httpClient = new HTTPClient("localhost", port, false);
    SEB.addClient(httpClient);
    SEB.addServer(server);
    HTTPRequest hr = hrb.build();
    ListenableFuture<HTTPResponse> lf = httpClient.sendRequest(hr);
    try{
      lf.get(5000, TimeUnit.MILLISECONDS);
    } catch(ExecutionException e) {
      assertTrue(lf.isDone());
      return;
    }
    assertTrue(false);
  }


  @Test
  public void onCloseWithData() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    final String response = new String("HTTP/1.1 200 OK\r\nAccept-Ranges: bytes\r\nContent-Type: text/html\r\n\r\nTEST");
    int port = Utils.findTCPPort();
    TCPServer server = new TCPServer("localhost", port);
    server.setClientAcceptor(new ClientAcceptor() {
      @Override
      public void accept(final Client c) {
        System.out.println("new Client!");
        TCPClient tc = (TCPClient)c; 
        tc.setReader(new Reader() {
          @Override
          public void onRead(Client client) {
            client.getRead();
            client.writeForce(ByteBuffer.wrap(response.getBytes()));
          }});
        SEB.addClient(c);
        PS.schedule(new Runnable() {
          @Override
          public void run() {
            c.close();
            System.out.println("ClientClosed!");
          }}, 200);
      }});
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    final HTTPClient httpClient = new HTTPClient("localhost", port, false);
    SEB.addClient(httpClient);
    SEB.addServer(server);
    HTTPRequest hr = hrb.build();
    ListenableFuture<HTTPResponse> lf = httpClient.sendRequest(hr);
    lf.get(5000, TimeUnit.MILLISECONDS);
    assertEquals("TEST", lf.get().getBodyAsString());
  }

  @Test
  public void onCloseWithDataRemaining() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    final String response = new String("HTTP/1.1 200 OK\r\nContent-Length: 11510\r\nAccept-Ranges: bytes\r\nContent-Type: text/html\r\n\r\nTEST");
    int port = Utils.findTCPPort();
    TCPServer server = new TCPServer("localhost", port);
    server.setClientAcceptor(new ClientAcceptor() {
      @Override
      public void accept(final Client c) {
        System.out.println("new Client!");
        TCPClient tc = (TCPClient)c; 
        tc.setReader(new Reader() {
          @Override
          public void onRead(Client client) {
            client.getRead();
            client.writeForce(ByteBuffer.wrap(response.getBytes()));
          }});
        SEB.addClient(c);
        PS.schedule(new Runnable() {
          @Override
          public void run() {
            c.close();
            System.out.println("ClientClosed!");
          }}, 200);
      }});
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    final HTTPClient httpClient = new HTTPClient("localhost", port, false);
    SEB.addClient(httpClient);
    SEB.addServer(server);
    HTTPRequest hr = hrb.build();
    ListenableFuture<HTTPResponse> lf = httpClient.sendRequest(hr);
    try{
      lf.get(5000, TimeUnit.MILLISECONDS);
    } catch(ExecutionException e) {
      assertTrue(lf.isDone());
      return;
    }
    assertTrue(false);
  }


  @Test
  public void badData() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    final String response = new String("BlahBlah\r\n\r\n");
    int port = Utils.findTCPPort();
    TCPServer server = new TCPServer("localhost", port);
    server.setClientAcceptor(new ClientAcceptor() {
      @Override
      public void accept(final Client c) {
        System.out.println("new Client!");
        TCPClient tc = (TCPClient)c; 
        tc.setReader(new Reader() {
          @Override
          public void onRead(Client client) {
            client.getRead();
            client.writeForce(ByteBuffer.wrap(response.getBytes()));
          }});
        SEB.addClient(c);
        PS.schedule(new Runnable() {
          @Override
          public void run() {
            c.close();
            System.out.println("ClientClosed!");
          }}, 200);
      }});
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    final HTTPClient httpClient = new HTTPClient("localhost", port, false);
    SEB.addClient(httpClient);
    SEB.addServer(server);
    HTTPRequest hr = hrb.build();
    ListenableFuture<HTTPResponse> lf = httpClient.sendRequest(hr);
    try{
      lf.get(5000, TimeUnit.MILLISECONDS);
    } catch(ExecutionException e) {
      assertTrue(lf.isDone());
      return;
    }
    assertTrue(false);
  }


  @Test
  public void badData2() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    final String response = new String("BlahBlah\r\n\r\ntest");
    int port = Utils.findTCPPort();
    TCPServer server = new TCPServer("localhost", port);
    server.setClientAcceptor(new ClientAcceptor() {
      @Override
      public void accept(final Client c) {
        System.out.println("new Client!");
        TCPClient tc = (TCPClient)c; 
        tc.setReader(new Reader() {
          @Override
          public void onRead(Client client) {
            client.getRead();
            client.writeForce(ByteBuffer.wrap(response.getBytes()));
          }});
        SEB.addClient(c);
      }});
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    final HTTPClient httpClient = new HTTPClient("localhost", port, false);
    SEB.addClient(httpClient);
    SEB.addServer(server);
    HTTPRequest hr = hrb.build();
    ListenableFuture<HTTPResponse> lf = httpClient.sendRequest(hr);
    try{
      lf.get(5000, TimeUnit.MILLISECONDS);
    } catch(ExecutionException e) {
      assertTrue(lf.isDone());
      return;
    }
    assertTrue(false);
  }


  @Test
  public void reconnectTest() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    final String response = new String("HTTP/1.1 200 OK\r\nContent-Length: 4\r\nAccept-Ranges: bytes\r\nContent-Type: text/html\r\n\r\nTEST");
    int port = Utils.findTCPPort();
    TCPServer server = new TCPServer("localhost", port);
    server.setClientAcceptor(new ClientAcceptor() {
      @Override
      public void accept(final Client c) {
        System.out.println("new Client!");
        TCPClient tc = (TCPClient)c; 
        tc.setReader(new Reader() {
          @Override
          public void onRead(Client client) {
            client.getRead();
            client.writeForce(ByteBuffer.wrap(response.getBytes()));
          }});
        SEB.addClient(c);
      }});
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"+port));
    final HTTPClient httpClient = new HTTPClient("localhost", port, false);
    SEB.addClient(httpClient);
    SEB.addServer(server);
    HTTPRequest hr = hrb.build();
    ListenableFuture<HTTPResponse> lf = httpClient.sendRequest(hr);
    lf.get(5000, TimeUnit.MILLISECONDS);
    httpClient.close();
    System.out.println(httpClient.isClosed());
    httpClient.sendRequest(hrb.build()).get(5000, TimeUnit.MILLISECONDS);
  }



  /*
  @Test
  public void multiTest() throws IOException, InterruptedException, ExecutionException, TimeoutException {
    MultiConnectionHttpClient mchc = new MultiConnectionHttpClient(SEB);
    final HTTPRequestBuilder hrb = new HTTPRequestBuilder(new URL("http://localhost:"));
    ListenableFuture<HTTPResponse> lf = mchc.sendRequest(hrb.build());
    System.out.println(lf.get(1000, TimeUnit.MILLISECONDS).rawHeaders);
    Thread.sleep(5000);
    lf = mchc.sendRequest(hrb.build());
    System.out.println(lf.get(1000, TimeUnit.MILLISECONDS).rawHeaders);


  }
   */
}
