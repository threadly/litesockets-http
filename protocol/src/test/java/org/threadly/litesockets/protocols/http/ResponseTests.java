package org.threadly.litesockets.protocols.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseBuilder;
import org.threadly.litesockets.protocols.http.response.HTTPResponseHeader;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;

public class ResponseTests {
  
  @Test
  public void responseCompareTest1() {
    HTTPResponseBuilder hrb = new HTTPResponseBuilder();
    HTTPResponse hr1 = hrb.build();
    HTTPResponse hr2 = hrb.build();
    assertEquals(hr1.hashCode(), hr2.hashCode());
    assertEquals(hr1, hr2);
    hr2 = hrb.setHeader("X-Custom", "blah").build();
    assertNotEquals(hr1, hr2);
    assertNotEquals(hr1.hashCode(), hr2.hashCode());
    assertEquals(hr1.getResponseHeader(), hr2.getResponseHeader());
    assertNotEquals(hr1.getHeaders(), hr2.getHeaders());
    hr1 = hrb.setHeader("X-Custom", "blah").build();
    assertEquals(hr1, hr2);
    assertEquals(hr1.hashCode(), hr2.hashCode());
    hr2 = hrb.setResponseHeader(new HTTPResponseHeader(HTTPResponseCode.Conflict, HTTPConstants.HTTP_VERSION_1_1)).build();
    assertNotEquals(hr1, hr2);
    assertNotEquals(hr1.hashCode(), hr2.hashCode());
    assertNotEquals(hr1.toString(), hr2.toString());
    assertNotEquals(hr1.getResponseHeader(), hr2.getResponseHeader());
    assertEquals(hr1.getHeaders(), hr2.getHeaders());
    hr2 = hr1.makeBuilder().build();
    assertEquals(hr1, hr2);
    assertEquals(hr1.hashCode(), hr2.hashCode());
    assertEquals(hr1.toString(), hr2.toString());
  }
}
