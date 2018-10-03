package org.threadly.litesockets.protocols.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Random;

import org.junit.Test;
import org.threadly.litesockets.protocols.ws.WSFrame;
import org.threadly.litesockets.protocols.ws.WSOPCode;
import org.threadly.litesockets.protocols.ws.WSUtils;


public class WebSocketsTests {

  @Test
  public void checkSmallSize() throws ParseException {
    simpleSizeParsing(5);
  }

  @Test
  public void checkMedSize() throws ParseException {
    simpleSizeParsing(6555);
  }

  @Test
  public void checkLargeSize() throws ParseException {
    simpleSizeParsing(165550);
  }

  public void simpleSizeParsing(int size) throws ParseException {
    WSFrame wsf = WSFrame.makeWSFrame(size, WSOPCode.Text.getValue(), true);
    WSFrame wsf2 = WSFrame.parseWSFrame(wsf.getRawFrame());
    assertEquals(wsf.isFinished(), wsf2.isFinished());
    assertEquals(wsf.getPayloadDataLength(), wsf2.getPayloadDataLength());
    assertEquals(wsf.getOpCode(), wsf2.getOpCode());
    assertEquals(wsf.hasRSV1(), wsf2.hasRSV1());
    assertEquals(wsf.hasRSV2(), wsf2.hasRSV2());
    assertEquals(wsf.hasRSV3(), wsf2.hasRSV3());
    assertEquals(wsf.hasMask(), wsf2.hasMask());
    assertEquals(wsf.getMaskValue(), wsf2.getMaskValue());
    assertTrue(Arrays.equals(wsf.getMaskArray(), wsf2.getMaskArray()));


    wsf = WSFrame.makeWSFrame(size, WSOPCode.Text.getValue(), false);
    wsf2 = WSFrame.parseWSFrame(wsf.getRawFrame());
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
    ByteBuffer bb = WSUtils.maskData(ByteBuffer.wrap(testString.getBytes()), mask);
    assertFalse(testString.equals(new String(bb.array())));
    ByteBuffer nbb = WSUtils.maskData(bb, mask);
    assertEquals(testString, new String(nbb.array()));
  }
}