package org.threadly.litesockets.protocols.http.response;

import java.nio.ByteBuffer;
import java.util.Map;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;


/**
 *  An Immutable HTTPResponse object.  This contains all information from an HTTP Response.
 *  
 *   NOTE: builder needed.
 */
public class HTTPResponse {
  private final HTTPResponseHeader rHeader;
  private final HTTPHeaders headers;
  
  public HTTPResponse(HTTPResponseHeader rHeader, HTTPHeaders headers) {
    this.rHeader = rHeader;
    this.headers = headers;
  }

  public HTTPResponse(HTTPResponseCode rCode, String httpVersion, Map<String, String> headers) {
    rHeader = new HTTPResponseHeader(rCode, httpVersion);
    this.headers = new HTTPHeaders(headers);
  }
  
  public HTTPResponseHeader getResponseHeader() {
    return rHeader;
  }
  
  public HTTPHeaders getHeaders() {
    return headers;
  }
  
  public ByteBuffer getByteBuffer() {
    ByteBuffer combined = ByteBuffer.allocate(headers.toString().length() + rHeader.length() + 
        HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length() + 
        HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length());
    combined.put(rHeader.getByteBuffer());
    combined.put(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.getBytes());
    combined.put(headers.toString().getBytes());
    combined.put(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.getBytes());
    combined.flip();
    return combined;
  }
  
  @Override
  public int hashCode() {
    return rHeader.hashCode() ^ headers.hashCode();
  }
  
  @Override
  public boolean equals(Object o) {
    if(o == this) {
      return true;
    } else if(o instanceof HTTPResponse) {
      HTTPResponse hr = (HTTPResponse)o;
      if(hr.rHeader.equals(rHeader) && hr.headers.equals(headers)) {
        return true;
      }
    }
    return false;
  }
  
  @Override
  public String toString() {
    return this.rHeader+HTTPConstants.HTTP_NEWLINE_DELIMINATOR+headers+HTTPConstants.HTTP_NEWLINE_DELIMINATOR;
  }
  
  public HTTPResponseBuilder makeBuilder() {
    return new HTTPResponseBuilder().setResponseHeader(rHeader).setHeaders(headers);
  }

}
