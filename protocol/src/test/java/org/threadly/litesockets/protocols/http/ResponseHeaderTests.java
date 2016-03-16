package org.threadly.litesockets.protocols.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;
import org.threadly.litesockets.protocols.http.response.HTTPResponseHeader;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;

public class ResponseHeaderTests {
  
  @Test
  public void ResponseHeaderTest1() {
    for(HTTPResponseCode hrc: HTTPResponseCode.values()) {
      String requestHead = "HTTP/1.0 "+hrc.getId()+" "+hrc.toString()+HTTPConstants.HTTP_NEWLINE_DELIMINATOR;
      HTTPResponseHeader hrh = new HTTPResponseHeader(requestHead);
      HTTPResponseHeader hrh2 = new HTTPResponseHeader(hrc, HTTPConstants.HTTP_VERSION_1_0);
      assertEquals(HTTPConstants.HTTP_VERSION_1_0, hrh.getHTTPVersion());
      assertEquals(hrc, hrh.getResponseCode());
      assertEquals(requestHead.length() -2, hrh.length());
      assertEquals(hrh, hrh2);
      assertEquals(hrh.hashCode(), hrh2.hashCode());
      assertEquals(hrh, hrh);
      assertEquals(hrh2, hrh2);
      ByteBuffer bb = hrh.getByteBuffer();
      byte[] ba = new byte[bb.remaining()];
      bb.get(ba);
      assertTrue(Arrays.equals(ba, requestHead.trim().getBytes()));
    }
  }
  
  @Test
  public void ResponseHeaderTest2() {
    for(HTTPResponseCode hrc: HTTPResponseCode.values()) {
      HTTPResponseHeader hrh = new HTTPResponseHeader(hrc, "HTTP/1.1");
      HTTPResponseHeader hrh2 =  new HTTPResponseHeader(hrh.toString());
      assertEquals(HTTPConstants.HTTP_VERSION_1_1, hrh.getHTTPVersion());
      assertEquals(hrc, hrh.getResponseCode());
      assertEquals(hrh2, hrh);
    }
  }
  
  @Test
  public void ResponseHeaderTest3() {
    HTTPResponseHeader hrh1 = new HTTPResponseHeader("HTTP/1.1 "+HTTPResponseCode.Accepted.getId()+" "+HTTPResponseCode.Accepted.toString());
    HTTPResponseHeader hrh2 = new HTTPResponseHeader(HTTPResponseCode.Accepted, "HTTP/1.0");
    HTTPResponseHeader hrh3 = new HTTPResponseHeader(HTTPResponseCode.AlreadyReported, "HTTP/1.1");
    
    assertNotEquals(hrh1, hrh2);
    assertNotEquals(hrh2, hrh3);
    assertNotEquals(hrh1, hrh3);
    assertNotEquals(hrh1, new Object());
  }
  
  @Test
  public void ResponseHeaderTest4() {
    for(HTTPResponseCode hrc: HTTPResponseCode.values()) {
      HTTPResponseHeader hrh = new HTTPResponseHeader(hrc, "HTTP/1.0");
      assertEquals(HTTPConstants.HTTP_VERSION_1_0, hrh.getHTTPVersion());
      assertEquals(hrc, hrh.getResponseCode());
    }
  }

  @Test
  public void BadResponseHeader() {
    try {
      new HTTPResponseHeader("HTTP/1.0 122 Not Here");
      fail("Should not make it this far");
    } catch(IllegalArgumentException e) {
      assertEquals(e.getMessage(), "Could not find ResponseCode: 122");
    }
  }
  
  @Test
  public void BadResponseHeader2() {
    try {
      new HTTPResponseHeader("HTTP/1.0 122NotHere");
      fail("Should not make it this far");
    } catch(IllegalArgumentException e) {
      System.out.println(e.getMessage());
      assertTrue(e.getMessage().contains("Invalide Response Header!"));
    }
  }
  
  @Test
  public void BadResponseHeader3() {
    try {
      new HTTPResponseHeader("HTTP/3.1 404 Not Found");
      fail("Should not make it this far");
    } catch(IllegalArgumentException e) {
      System.out.println(e.getMessage());
      assertEquals("Unknown HTTP Version!:HTTP/3.1", e.getMessage());
    }
  }
  
  @Test
  public void BadResponseHeader4() {
    try {
      new HTTPResponseHeader(HTTPResponseCode.Accepted, "HTTP/3.1");
      fail("Should not make it this far");
    } catch(IllegalArgumentException e) {
      System.out.println(e.getMessage());
      assertEquals("Unknown HTTP Version!:HTTP/3.1", e.getMessage());
    }
  }

  @Test
  public void BadResponseHeader5() {
    try {
      new HTTPResponseHeader(HTTPResponseCode.NotFound, "HTTP/3.1");
      fail("Should not make it this far");
    } catch(IllegalArgumentException e) {
      System.out.println(e.getMessage());
      assertEquals("Unknown HTTP Version!:HTTP/3.1", e.getMessage());
    }
  }

}
