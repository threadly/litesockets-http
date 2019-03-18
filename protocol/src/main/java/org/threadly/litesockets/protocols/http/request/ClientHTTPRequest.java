package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;

import org.threadly.litesockets.protocols.http.shared.HTTPAddress;

// TODO - do we want to move this into the `client`?  I see the `HTTPRequestBuilder` references it, 
// but we could create an extending class `ClientHTTPRequestBuilder` which can build this
/**
 * This contains a full HTTPRequest, including the HTTPRequest, the HTTPAddress the body and the timeout.
 * This is immutable, though an HTTPRequestBuilder can be made from it.
 */
public class ClientHTTPRequest {
  private final HTTPRequest request;
  private final HTTPAddress ha;
  private final ByteBuffer bodyBytes;
  private final int timeoutMS; 
  
  protected ClientHTTPRequest(HTTPRequest request, HTTPAddress ha, int timeoutMS, ByteBuffer bodyBytes) {
    this.request = request;
    this.ha = ha;
    this.bodyBytes = bodyBytes;
    this.timeoutMS = timeoutMS;
  }
  
  public HTTPRequest getHTTPRequest() {
    return request;
  }

  /**
   * Returns the {@link HTTPAddress} the request is associated with.
   * 
   * @return The {@link HTTPAddress} the request will go to
   */
  public HTTPAddress getHTTPAddress() {
    return ha;
  }

  // TODO - does this make sense, it seems dangerous since the buffer could be consumed / read.
  // We could return a slice / duplicate copy to avoid this, or we can change this to `hasBodyContent` 
  // (which seems how this is being used)
  /**
   * Returns the body data the request was constructed with.
   * 
   * @return The ByteBuffer representing the body
   */
  public ByteBuffer getBodyBuffer() {
    return bodyBytes;
  }
  
  /**
   * Returns the timeout value that this request was constructed with.
   * 
   * @return The request timeout in milliseconds
   */
  public int getTimeoutMS() {
    return this.timeoutMS;
  }

  // TODO - is this useful?  I am not seeing any cases of it being used
  public HTTPRequestBuilder makeBuilder() {
    HTTPRequestBuilder hrb = request.makeBuilder();
    hrb.setHTTPAddress(ha, false);
        
    return hrb;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((bodyBytes == null) ? 0 : bodyBytes.hashCode());
    result = prime * result + ((ha == null) ? 0 : ha.hashCode());
    result = prime * result + ((request == null) ? 0 : request.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ClientHTTPRequest other = (ClientHTTPRequest) obj;
    if (bodyBytes == null ) {
      if (other.bodyBytes != null) {
        return false;
      }
    } else if (!bodyBytes.equals(other.bodyBytes))
      return false;
    if (ha == null) {
      if (other.ha != null) {
        return false;
      }
    } else if (!ha.equals(other.ha))
      return false;
    if (request == null) {
      if (other.request != null) {
        return false;
      }
    } else if (!request.equals(other.request)) {
      return false;
    }
    return false;
  }
  
}
