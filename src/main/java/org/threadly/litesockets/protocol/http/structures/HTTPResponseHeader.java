package org.threadly.litesockets.protocol.http.structures;

import org.threadly.litesockets.protocol.http.HTTPConstants;

public class HTTPResponseHeader {
  public final String rawResponse;
  public final String responseValue;
  public final String responseText;
  public final String httpVersion;
  
  public HTTPResponseHeader(String rawResponse) {
    this.rawResponse = rawResponse.trim().intern();
    String[] tmp = this.rawResponse.split(" ", 3);
    httpVersion = tmp[0].trim().toUpperCase().intern();
    responseValue = tmp[1].trim().intern();
    responseText = HTTPConstants.RESPONSE_CODES.get(responseValue);
    if(responseText == null) {
      throw new IllegalStateException("Response Code is Unknown!:"+responseValue);
    }
    if(!httpVersion.equals(HTTPConstants.HTTP_VERSION_1_1) && !httpVersion.equals(HTTPConstants.HTTP_VERSION_1_0)) {
      throw new IllegalStateException("Unknown HTTP Version!:"+httpVersion);
    }
  }
  
  public HTTPResponseHeader(String rCode, String httpVersion) {
    responseText = HTTPConstants.RESPONSE_CODES.get(rCode);
    responseValue = rCode;
    this.httpVersion = httpVersion;
    this.rawResponse = rCode+HTTPConstants.SPACE+responseText+HTTPConstants.SPACE+httpVersion;
    if(!httpVersion.equals(HTTPConstants.HTTP_VERSION_1_1) && !httpVersion.equals(HTTPConstants.HTTP_VERSION_1_0)) {
      throw new IllegalStateException("Unknown HTTP Version!:"+httpVersion);
    }
  }
  
  public String getResponseCode() {
    return this.responseValue;
  }
  

}
