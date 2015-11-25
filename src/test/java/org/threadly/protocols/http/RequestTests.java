package org.threadly.protocols.http;

import org.junit.Test;
import org.threadly.protocols.http.request.HTTPRequestProcessor;

public class RequestTests {

  @Test
  public void basicParsingTest() {
    HTTPRequestProcessor hrp = new HTTPRequestProcessor(); 
    hrp.processData(Constants.HTTP_REQUEST_GET_BASIC.getBytes());
  }
}
