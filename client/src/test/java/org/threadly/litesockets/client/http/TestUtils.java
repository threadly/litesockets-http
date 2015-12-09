package org.threadly.litesockets.client.http;

import java.io.IOException;
import java.net.ServerSocket;

public class TestUtils {
  
  public static int findTCPPort() {
    try {
      ServerSocket s = new ServerSocket(0);
      int port = s.getLocalPort();
      s.close();
      return port;
    } catch(IOException e) {
      //We Dont Care
    }
    throw new RuntimeException("Could not find a port!!");
  }

}
