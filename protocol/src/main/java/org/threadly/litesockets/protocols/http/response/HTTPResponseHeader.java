package org.threadly.litesockets.protocols.http.response;

import java.nio.ByteBuffer;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;



/**
 * An Immutable object of the HTTP Response header.  Basically the first line in the Header of an HTTP response. 
 */
public class HTTPResponseHeader {
  private static final int REQUIRED_RESPONSE_ITEMS = 3;
  private final byte[] rawResponse;
  private final HTTPResponseCode hrc;
  private final String httpVersion;
  
  public HTTPResponseHeader(String stringResponse) {
    
    this.rawResponse = stringResponse.trim().getBytes();
    String[] tmp = stringResponse.trim().split(" ");
    if(tmp.length != REQUIRED_RESPONSE_ITEMS) {
      throw new IllegalArgumentException("HTTPResponseHeader can only have 3 arguments! :"+stringResponse);
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
    this.httpVersion = httpVersion.intern();
    rawResponse = (this.httpVersion+" "+hrc.getId()+" "+hrc.toString()).getBytes();
  }
  
  public int length() {
    return this.rawResponse.length;
  }
  
  public ByteBuffer getByteBuffer() {
    return ByteBuffer.wrap(this.rawResponse);
  }
  
  public HTTPResponseCode getResponseCode() {
    return hrc;
  }
  
  public String getHTTPVersion() {
    return httpVersion;
  }
  
  @Override
  public int hashCode() {
    return hrc.hashCode() | httpVersion.hashCode();
  }
  
  @Override
  public boolean equals(Object o) {
    if(o instanceof HTTPResponseHeader) {
      return ((HTTPResponseHeader)o).hrc.equals(hrc) && ((HTTPResponseHeader)o).httpVersion.equals(httpVersion);
    }
    return false;
  }
  
  @Override
  public String toString() {
    return new String(rawResponse);
  }
  

}
