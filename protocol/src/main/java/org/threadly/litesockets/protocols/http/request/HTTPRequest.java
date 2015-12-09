package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;

/**
 * This is an immutable HTTPRequest object.  This is what is sent to the server when doing an
 * HTTPRequest.  This object is created via {@link HTTPRequestBuilder}. 
 */
public class HTTPRequest {
  private final HTTPRequestHeader request;
  private final HTTPHeaders headers;
  
  protected HTTPRequest(HTTPRequestHeader request, HTTPHeaders headers) {
    this.request = request;
    this.headers = headers;
  }
  
  public HTTPHeaders getHTTPHeaders() {
    return headers;
  }
  
  public HTTPRequestHeader getHTTPRequestHeaders() {
    return request;
  }
  
  public ByteBuffer getByteBuffer() {
    ByteBuffer combined = ByteBuffer.allocate(headers.toString().length() + request.length() + 
        HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length() + 
        HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length());
    
    combined.put(request.getByteBuffer());
    combined.put(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.getBytes());
    combined.put(headers.toString().getBytes());
    combined.put(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.getBytes());
    combined.flip();
    return combined;
  }
  
  @Override
  public String toString() {
    return request.toString()+
        HTTPConstants.HTTP_NEWLINE_DELIMINATOR+
        headers.toString()+
        HTTPConstants.HTTP_NEWLINE_DELIMINATOR;
  }
  
  @Override
  public int hashCode() {
    return request.hashCode() ^ headers.hashCode();
  }
  
  @Override
  public boolean equals(Object o) {
    if(o instanceof HTTPRequest) {
      HTTPRequest hr = (HTTPRequest)o;
      if(hr.request.equals(request) && hr.headers.equals(headers)) {
        return true;
      }
    }
    return false;
  }
  
  public HTTPRequestBuilder makeBuilder() {
    HTTPRequestBuilder hrb = new HTTPRequestBuilder().setHTTPHeaders(headers).setHTTPRequestHeader(request);
    return hrb;
  }
  
}
