package org.threadly.litesockets.protocols.http.response;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;



/**
 * An Immutable object of the HTTP Response header.  Basically the first line in the Header of an HTTP response. 
 */
public class HTTPResponseHeader {
  public static final int REQUIRED_RESPONSE_ITEMS = 3;
  public final String rawResponse;
  public final String responseValue;
  public final String responseText;
  public final String httpVersion;
  
  public HTTPResponseHeader(String rawResponse) {
    this.rawResponse = rawResponse.trim().intern();
    String[] tmp = this.rawResponse.split(" ");
    if(tmp.length != REQUIRED_RESPONSE_ITEMS) {
      throw new IllegalArgumentException("HTTPResponseHeader can only have 3 arguments! :"+rawResponse);
    }
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
