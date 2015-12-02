package org.threadly.litesockets.protocols.http;

import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.request.HTTPRequestProcessor;
import org.threadly.litesockets.protocols.http.request.HTTPRequestProcessor.HTTPRequestCallback;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;
import org.threadly.litesockets.protocols.http.shared.RequestType;

public class RequestTests {
  public static final String DATA = "1234567890";
  public static final byte[] DATA_BA = "1234567890".getBytes();
  
  
  HTTPRequestBuilder hrb;
  HTTPCB cb;
  HTTPRequestProcessor hrp;
  
  @Before
  public void start() throws MalformedURLException {
    hrb = new HTTPRequestBuilder(new URL("https://test.com/test12334?query=1"));
    cb = new HTTPCB();
    hrp = new HTTPRequestProcessor(); 
  }
  
  @Test
  public void builderQueryCheck() {
    for(int i=0; i<20; i++) {
      String nq = "query"+i;
      hrb.appedQuery(nq, Integer.toString(i));
      HTTPRequest hr = hrb.build();
      assertEquals(Integer.toString(i), hr.getHTTPRequestHeaders().getRequestQuery().get(nq));
    }
    for(int i=0; i<20; i++) {
      String nq = "query"+i;
      hrb.removeQuery(nq);
      HTTPRequest hr = hrb.build();
      assertTrue(hr.getHTTPHeaders().getHeader(nq) == null);
    }
  }
  
  @Test
  public void builderRequestTypeCheck() {
    for(RequestType rt: RequestType.values()) {
      hrb.setRequestType(rt);
      HTTPRequest hr = hrb.build();
      assertEquals(rt.toString(), hr.getHTTPRequestHeaders().getRequestType());
    }
    hrb.setRequestType("BLAH");
    HTTPRequest hr = hrb.build();
    assertEquals("BLAH", hr.getHTTPRequestHeaders().getRequestType());
  }
  
  @Test
  public void builderHeadersCheck() {
    for(int i=0; i<20; i++) {
      String nh = "X-NewHeader-"+i;
      hrb.setHeader(nh, Integer.toString(i));
      HTTPRequest hr = hrb.build();
      assertEquals(Integer.toString(i), hr.getHTTPHeaders().getHeader(nh));
    }
    for(int i=0; i<20; i++) {
      String nh = "X-NewHeader-"+i;
      hrb.removeHeader(nh);
      HTTPRequest hr = hrb.build();
      assertTrue(hr.getHTTPHeaders().getHeader(nh) == null);
    }
  }

  @Test
  public void basicBuildAndParsingTest() throws MalformedURLException {
    hrb = hrb.setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, "0").duplicate();
    hrp.addHTTPRequestCallback(cb);
    HTTPRequest hr = hrb.build();
    hrp.processData(hr.getCombinedBuffers());
    assertTrue(cb.finished);
    assertEquals(0, cb.bbs.size());
    assertEquals(hr, cb.request);
    assertTrue(cb.error == null);
    assertEquals(HTTPConstants.HTTP_VERSION_1_1, cb.request.getHTTPRequestHeaders().getHttpVersion());
    assertEquals("/test12334", cb.request.getHTTPRequestHeaders().getRequestPath());
    assertEquals(RequestType.GET.toString(), cb.request.getHTTPRequestHeaders().getRequestType());
    assertEquals("1", cb.request.getHTTPRequestHeaders().getRequestQuery().get("query"));
    assertEquals(hr, cb.request);
    assertEquals(hr.toString(), cb.request.toString());
  }
  
  @Test
  public void basicBuildAndParsingWithDataTest() throws MalformedURLException {
    hrb.setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, "10");
    hrp.addHTTPRequestCallback(cb);
    HTTPRequest hr = hrb.build();
    hrp.processData(hr.getCombinedBuffers());
    
    assertFalse(cb.finished);
    assertEquals(0, cb.bbs.size());
    assertFalse(cb.request == null);
    assertTrue(cb.error == null);
    assertEquals(HTTPConstants.HTTP_VERSION_1_1, cb.request.getHTTPRequestHeaders().getHttpVersion());
    assertEquals("/test12334", cb.request.getHTTPRequestHeaders().getRequestPath());
    assertEquals(RequestType.GET.toString(), cb.request.getHTTPRequestHeaders().getRequestType());
    assertEquals("1", cb.request.getHTTPRequestHeaders().getRequestQuery().get("query"));
    hrp.processData(DATA_BA);
    assertTrue(cb.finished);
    assertEquals(1, cb.bbs.size());
    assertEquals(DATA, HTTPUtils.bbToString(cb.bbs.get(0).duplicate()));
    assertEquals(hr.toString(), cb.request.toString());
  }
  
  @Test
  public void basicBuildAndParsingNoCL() throws MalformedURLException {
    hrb.setURL(new URL("https://test.com/test12334?query=1"));
    hrp.addHTTPRequestCallback(cb);
    HTTPRequest hr = hrb.build();
    hrp.processData(hr.getCombinedBuffers());
    
    assertFalse(cb.finished);
    assertEquals(0, cb.bbs.size());
    assertFalse(cb.request == null);
    assertTrue(cb.error == null);
    assertEquals(HTTPConstants.HTTP_VERSION_1_1, cb.request.getHTTPRequestHeaders().getHttpVersion());
    assertEquals("/test12334", cb.request.getHTTPRequestHeaders().getRequestPath());
    assertEquals(RequestType.GET.toString(), cb.request.getHTTPRequestHeaders().getRequestType());
    assertEquals("1", cb.request.getHTTPRequestHeaders().getRequestQuery().get("query"));
    hrp.processData(DATA_BA);
    assertEquals(1, cb.bbs.size());
    assertEquals(DATA, HTTPUtils.bbToString(cb.bbs.get(0).duplicate()));
    hrp.processData(DATA_BA);
    assertFalse(cb.finished);
    assertEquals(2, cb.bbs.size());
    assertEquals(DATA, HTTPUtils.bbToString(cb.bbs.get(1).duplicate()));
    hrp.connectionClosed();
    assertTrue(cb.finished);
    assertEquals(hr.toString(), cb.request.toString());
  }

  
  @Test
  public void basicParsingRequestHeaderToLarge() throws MalformedURLException {
    hrb.setURL(new URL("https://test.com/test12334?query=1"));
    hrp.addHTTPRequestCallback(cb);
    StringBuilder sb = new StringBuilder();
    for(int i=0; i<HTTPRequestProcessor.MAX_HEADER_ROW_LENGTH; i++) {
      sb.append("A");
    }
    sb.append("A");
    hrb.appedQuery("X-CUSTOM", sb.toString());
    HTTPRequest hr = hrb.build();
    hrp.processData(hr.getCombinedBuffers());
    assertTrue(cb.error != null);
    assertTrue(cb.error instanceof HTTPParsingException);
  }

  
  @Test
  public void basicParsingHeadersToLarge() throws MalformedURLException {
    hrb.setURL(new URL("https://test.com/test12334?query=1"));
    hrp.addHTTPRequestCallback(cb);
    StringBuilder sb = new StringBuilder();
    for(int i=0; i<HTTPRequestProcessor.MAX_HEADER_LENGTH; i++) {
      sb.append("A");
    }
    sb.append("A");
    hrb.setHeader("X-CUSTOM", sb.toString());
    HTTPRequest hr = hrb.build();
    hrp.processData(hr.getCombinedBuffers());
    assertTrue(cb.error != null);
    assertTrue(cb.error instanceof HTTPParsingException);
  }
  
  @Test
  public void basicParsingChunkedBadChunkSize() throws MalformedURLException {
    hrb.setURL(new URL("https://test.com/test12334?query=1")).setHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING, "chunked");
    hrp.addHTTPRequestCallback(cb);
    HTTPRequest hr = hrb.build();
    hrp.processData(hr.getCombinedBuffers());
    hrp.processData("TRE\r\n".getBytes());
    assertTrue(cb.error != null);
    assertTrue(cb.error instanceof HTTPParsingException);
  }
  
  @Test
  public void basicParsingChunkedManyReads() throws MalformedURLException {
    hrb.setURL(new URL("https://test.com/test12334?query=1")).setHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING, "chunked");
    hrp.addHTTPRequestCallback(cb);
    HTTPRequest hr = hrb.build();
    hrp.processData(hr.getCombinedBuffers());
    assertEquals(hr, cb.request);
    assertEquals(hr.toString(), cb.request.toString());
    byte[] ba = HTTPUtils.wrapInChunk(DATA_BA);
    for(int i = 0; i<ba.length; i++) {
      byte[] nba = new byte[1];
      nba[0] = ba[i];
      hrp.processData(nba);
    }
    hrp.processData(HTTPUtils.wrapInChunk(ByteBuffer.allocate(0)));
    assertTrue(cb.finished);
  }
  
  @Test
  public void basicBuildAndParsingChunked() throws MalformedURLException {
    hrb.setURL(new URL("https://test.com/test12334?query=1")).setHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING, "chunked");
    hrp.addHTTPRequestCallback(cb);
    HTTPRequest hr = hrb.build();
    hrp.processData(hr.getCombinedBuffers());
    
    
    assertFalse(cb.finished);
    assertEquals(0, cb.bbs.size());
    assertFalse(cb.request == null);
    assertTrue(cb.error == null);
    assertEquals(HTTPConstants.HTTP_VERSION_1_1, cb.request.getHTTPRequestHeaders().getHttpVersion());
    assertEquals("/test12334", cb.request.getHTTPRequestHeaders().getRequestPath());
    assertEquals(RequestType.GET.toString(), cb.request.getHTTPRequestHeaders().getRequestType());
    assertEquals("1", cb.request.getHTTPRequestHeaders().getRequestQuery().get("query"));
    hrp.processData(HTTPUtils.wrapInChunk(DATA_BA));
    assertEquals(1, cb.bbs.size());
    assertEquals(DATA, HTTPUtils.bbToString(cb.bbs.get(0).duplicate()));
    hrp.processData(HTTPUtils.wrapInChunk(DATA_BA));
    assertFalse(cb.finished);
    assertEquals(2, cb.bbs.size());
    assertEquals(DATA, HTTPUtils.bbToString(cb.bbs.get(1).duplicate()));
    hrp.processData(HTTPUtils.wrapInChunk(ByteBuffer.allocate(0)));
    
    assertTrue(cb.finished);
    assertEquals(hr.toString(), cb.request.toString());
  }
  
  @Test
  public void backToBackCLTest() throws MalformedURLException {
    hrb.setURL(new URL("https://test.com"));
    hrb.setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, "10");
    hrp.addHTTPRequestCallback(cb);
    HTTPRequest hr = hrb.build();
    
    ByteBuffer bb = ByteBuffer.allocate((hr.getCombinedBuffers().remaining()*10) + 10*10);
    for(int i=0;i <10; i++) {
      bb.put(hr.getCombinedBuffers());
      bb.put(DATA_BA);
    }
    bb.flip();
    hrp.processData(bb);
    assertEquals(10, cb.finishedCalls);
    assertEquals(10, cb.finishedHeadersCalls);
    assertEquals(10, cb.bbs.size());
    for(ByteBuffer bb2: cb.bbs) {
      assertEquals(DATA, HTTPUtils.bbToString(bb2));
    }
  }
  
  public static class HTTPCB implements HTTPRequestCallback {
    
    public Throwable error = null;
    public boolean finished = false;
    public HTTPRequest request = null;
    public List<ByteBuffer> bbs = new ArrayList<ByteBuffer>();
    public int finishedCalls = 0;
    public int errorCalls = 0;
    public int finishedHeadersCalls = 0;

    public void reset() {
      error = null;
      finished = false;
      request = null;
      bbs = new ArrayList<ByteBuffer>();
    }
    
    @Override
    public void headersFinished(HTTPRequest hr) {
      request = hr;
      finishedHeadersCalls++;
    }

    @Override
    public void bodyData(ByteBuffer bb) {
      bbs.add(bb);
    }

    @Override
    public void finished() {
      this.finished = true;
      this.finishedCalls++;
    }

    @Override
    public void hasError(Throwable t) {
      error = t;
      this.errorCalls++;
    }
    
  }
}
