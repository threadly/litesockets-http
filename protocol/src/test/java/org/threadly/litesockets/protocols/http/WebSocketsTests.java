package org.threadly.litesockets.protocols.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import org.junit.Test;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.future.FutureCallback;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.client.http.HTTPStreamClient;
import org.threadly.litesockets.client.http.HTTPStreamClient.HTTPStreamReader;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.litesockets.protocols.http.shared.WebSocketFrameParser;
import org.threadly.litesockets.protocols.http.shared.WebSocketFrameParser.WebSocketFrame;
import org.threadly.litesockets.protocols.http.shared.WebSocketFrameParser.WebSocketOpCodes;
import org.threadly.litesockets.utils.MergedByteBuffers;

public class WebSocketsTests {
  



  @Test
  public void checkSmallSize() {
    simpleSizeParsing(5);
  }
  
  @Test
  public void checkMedSize() {
    simpleSizeParsing(6555);
  }
  
  @Test
  public void checkLargeSize() {
    simpleSizeParsing(165550);
  }
  
  public void simpleSizeParsing(int size) {
    WebSocketFrame wsf = WebSocketFrameParser.makeWebSocketFrame(size, WebSocketOpCodes.Text.getValue(), true);
    WebSocketFrame wsf2 = WebSocketFrameParser.parseWebSocketFrame(wsf.getRawFrame());
    assertEquals(wsf.isFinished(), wsf2.isFinished());
    assertEquals(wsf.getPayloadDataLength(), wsf2.getPayloadDataLength());
    assertEquals(wsf.getOpCode(), wsf2.getOpCode());
    assertEquals(wsf.hasRSV1(), wsf2.hasRSV1());
    assertEquals(wsf.hasRSV2(), wsf2.hasRSV2());
    assertEquals(wsf.hasRSV3(), wsf2.hasRSV3());
    assertEquals(wsf.hasMask(), wsf2.hasMask());
    assertEquals(wsf.getMaskValue(), wsf2.getMaskValue());
    assertTrue(Arrays.equals(wsf.getMaskArray(), wsf2.getMaskArray()));
    
    
    wsf = WebSocketFrameParser.makeWebSocketFrame(size, WebSocketOpCodes.Text.getValue(), false);
    wsf2 = WebSocketFrameParser.parseWebSocketFrame(wsf.getRawFrame());
    assertEquals(wsf.isFinished(), wsf2.isFinished());
    assertEquals(wsf.getPayloadDataLength(), wsf2.getPayloadDataLength());
    assertEquals(wsf.getOpCode(), wsf2.getOpCode());
    assertEquals(wsf.hasRSV1(), wsf2.hasRSV1());
    assertEquals(wsf.hasRSV2(), wsf2.hasRSV2());
    assertEquals(wsf.hasRSV3(), wsf2.hasRSV3());
    assertEquals(wsf.hasMask(), wsf2.hasMask());
    assertEquals(wsf.getMaskValue(), wsf2.getMaskValue());
    assertTrue(Arrays.equals(wsf.getMaskArray(), wsf2.getMaskArray()));
  }

  @Test
  public void maskingTest() {
    Random rnd = new Random();
    int mask = rnd.nextInt();
    String test = "TEST1";
    StringBuilder sb = new StringBuilder();
    for(int i=0; i<300; i++) {
      sb.append(test);
    }
    String testString = sb.toString();
    ByteBuffer bb = WebSocketFrameParser.unmaskData(ByteBuffer.wrap(testString.getBytes()), mask);
    assertFalse(testString.equals(new String(bb.array())));
    ByteBuffer nbb = WebSocketFrameParser.unmaskData(bb, mask);
    assertEquals(testString, new String(nbb.array()));
  }
}