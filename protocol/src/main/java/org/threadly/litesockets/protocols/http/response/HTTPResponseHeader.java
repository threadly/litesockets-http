package org.threadly.litesockets.protocols.http.response;

import java.nio.ByteBuffer;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;

/**
 * An Immutable object of the HTTP Response header.  Basically the first line in the Header of an HTTP response. 
 */
public class HTTPResponseHeader {
  private static final int MAX_RESPONSE_ITEMS = 3;
  private final String rawResponse;
  private final HTTPResponseCode hrc;
  private final String httpVersion;
  
  public HTTPResponseHeader(String stringResponse) {
    this.rawResponse = stringResponse.trim();
    String[] tmp = stringResponse.trim().split(" ", MAX_RESPONSE_ITEMS);
    try {
      httpVersion = tmp[0].trim();
      if(!httpVersion.equalsIgnoreCase(HTTPConstants.HTTP_VERSION_1_1) && !httpVersion.equalsIgnoreCase(HTTPConstants.HTTP_VERSION_1_0)) {
        throw new IllegalArgumentException("Unknown HTTP Version!:"+httpVersion);
      }
      hrc = HTTPResponseCode.findResponseCode(Integer.parseInt(tmp[1].trim()));
    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
      throw new IllegalArgumentException("Invalid Response Header! :"+stringResponse);
    }
  }
  
  public HTTPResponseHeader(HTTPResponseCode rCode, String httpVersion) {
    if(!httpVersion.equals(HTTPConstants.HTTP_VERSION_1_1) && !httpVersion.equals(HTTPConstants.HTTP_VERSION_1_0)) {
      throw new IllegalArgumentException("Unknown HTTP Version!:"+httpVersion);
    }
    hrc = rCode;
    this.httpVersion = httpVersion;
    rawResponse = (this.httpVersion+" "+hrc.getId()+" "+hrc.toString());
  }
  
  public int length() {
    return rawResponse.length();
  }
  
  public ByteBuffer getByteBuffer() {
    return ByteBuffer.wrap(this.rawResponse.getBytes()).asReadOnlyBuffer();
  }
  
  public HTTPResponseCode getResponseCode() {
    return hrc;
  }
  
  public String getHTTPVersion() {
    return httpVersion;
  }
  
  @Override
  public int hashCode() {
    return rawResponse.hashCode();
  }
  
  @Override
  public boolean equals(Object o) {
    if(o == this) {
      return true;
    } else if (o instanceof HTTPResponseHeader) {
      return ((HTTPResponseHeader)o).hrc.equals(hrc) && ((HTTPResponseHeader)o).httpVersion.equals(httpVersion);
    }
    return false;
  }
  
  @Override
  public String toString() {
    return rawResponse;
  }
  

}
