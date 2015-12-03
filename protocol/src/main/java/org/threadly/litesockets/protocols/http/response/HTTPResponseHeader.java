package org.threadly.litesockets.protocols.http.response;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;



/**
 * An Immutable object of the HTTP Response header.  Basically the first line in the Header of an HTTP response. 
 */
public class HTTPResponseHeader {
  public static final int REQUIRED_RESPONSE_ITEMS = 3;
  public final String rawResponse;
  public final HTTPResponseCode hrc;
  public final String httpVersion;
  
  public HTTPResponseHeader(String rawResponse) {
    this.rawResponse = rawResponse.trim().intern();
    String[] tmp = this.rawResponse.split(" ");
    if(tmp.length != REQUIRED_RESPONSE_ITEMS) {
      throw new IllegalArgumentException("HTTPResponseHeader can only have 3 arguments! :"+rawResponse);
    }
    httpVersion = tmp[0].trim().toUpperCase().intern();
    if(!httpVersion.equals(HTTPConstants.HTTP_VERSION_1_1) && !httpVersion.equals(HTTPConstants.HTTP_VERSION_1_0)) {
      throw new IllegalStateException("Unknown HTTP Version!:"+httpVersion);
    }
    hrc = HTTPResponseCode.findResponseCode(Integer.parseInt(tmp[1].trim()));
  }
  
  public HTTPResponseHeader(HTTPResponseCode rCode, String httpVersion) {
    if(!httpVersion.equals(HTTPConstants.HTTP_VERSION_1_1) && !httpVersion.equals(HTTPConstants.HTTP_VERSION_1_0)) {
      throw new IllegalStateException("Unknown HTTP Version!:"+httpVersion);
    }
    hrc = rCode;
    this.httpVersion = httpVersion;
    rawResponse = hrc.getId()+" "+hrc.toString()+" "+httpVersion;
  }
  
  public HTTPResponseCode getResponseCode() {
    return hrc;
  }
  
  public String getHTTPVersion() {
    return httpVersion;
  }
  
  @Override
  public String toString() {
    return rawResponse;
  }
  

}
