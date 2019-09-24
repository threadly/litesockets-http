package org.threadly.litesockets.protocols.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseBuilder;
import org.threadly.litesockets.protocols.http.response.HTTPResponseHeader;
import org.threadly.litesockets.protocols.http.response.HTTPResponseProcessor;
import org.threadly.litesockets.protocols.http.response.HTTPResponseProcessor.HTTPResponseCallback;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;
import org.threadly.litesockets.protocols.websocket.WSFrame;

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
  
  @Test
  public void responseProcessorBasicTest() throws Exception {
    HTTPResponseProcessor hrp = new HTTPResponseProcessor(false);
    final SettableListenableFuture<HTTPResponse> header = new SettableListenableFuture<>(false);
    final SettableListenableFuture<Boolean> finished = new SettableListenableFuture<>(false);
    hrp.addHTTPResponseCallback(new HTTPResponseCallback() {
      @Override
      public void headersFinished(HTTPResponse hr) {
        header.setResult(hr);
      }

      @Override
      public void bodyData(ByteBuffer bb) {
        // ignored
      }

      @Override
      public void finished() {
        finished.setResult(true);
      }

      @Override
      public void hasError(Throwable t) {
        finished.setFailure(t);
        header.setFailure(t);
      }

      @Override
      public void websocketData(WSFrame wsf, ByteBuffer bb) {
        hasError(new Exception("Unexpected websocket data"));
      }});
    HTTPResponse hr = new HTTPResponseBuilder().build();
    hrp.processData(hr.getMergedByteBuffers());
    assertEquals(hr, header.get(5,TimeUnit.SECONDS));
    assertTrue(finished.get(5,TimeUnit.SECONDS));
  }
  
  @Test
  public void responseProcessorChunkedEncodingEarlyCloseTest() throws Exception {
    HTTPResponseProcessor hrp = new HTTPResponseProcessor(false);
    final SettableListenableFuture<HTTPResponse> header = new SettableListenableFuture<>(false);
    final SettableListenableFuture<Boolean> finished = new SettableListenableFuture<>(false);
    hrp.addHTTPResponseCallback(new HTTPResponseCallback() {
      @Override
      public void headersFinished(HTTPResponse hr) {
        header.setResult(hr);
      }

      @Override
      public void bodyData(ByteBuffer bb) {
        // ignored
      }

      @Override
      public void finished() {
        finished.setResult(true);
      }

      @Override
      public void hasError(Throwable t) {
        finished.setFailure(t);
        header.setFailure(t);
      }

      @Override
      public void websocketData(WSFrame wsf, ByteBuffer bb) {
        hasError(new Exception("Unexpected websocket data"));
      }});
    HTTPResponse hr = 
        new HTTPResponseBuilder()
            .setHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING, "chunked")
            .build();
    
    hrp.processData(hr.getMergedByteBuffers());
    hrp.processData(HTTPUtils.wrapInChunk(ByteBuffer.wrap(new byte[10])));
    hrp.connectionClosed();
    
    assertEquals(hr, header.get(5,TimeUnit.SECONDS));
    assertTrue(finished.get(5,TimeUnit.SECONDS));
  }
}
