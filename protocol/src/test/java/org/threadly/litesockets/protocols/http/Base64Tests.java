package org.threadly.litesockets.protocols.http;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;
import org.threadly.litesockets.protocols.utils.Base64;

public class Base64Tests {

  @Test
  public void simpleTest() {
    Random rnd = new Random();
    for(int i=0; i<1000; i++) {
      byte[] ba = new byte[i];
      rnd.nextBytes(ba);
      String s1 = Base64.encode(ba);
      byte[] ba2 = Base64.decode(s1);
      assertTrue(Arrays.equals(ba, ba2));
    }
  }
}
