package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;

import org.threadly.litesockets.protocols.http.shared.HTTPAddress;

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

  public HTTPAddress getHTTPAddress() {
    return ha;
  }

  public ByteBuffer getBodyBuffer() {
    return bodyBytes;
  }
  
  public int getTimeoutMS() {
    return this.timeoutMS;
  }

  public HTTPRequestBuilder makeBuilder() {
    HTTPRequestBuilder hrb = request.makeBuilder();
    hrb.setHTTPAddress(ha);
        
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
