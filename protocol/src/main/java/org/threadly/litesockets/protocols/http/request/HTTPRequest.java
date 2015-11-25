package org.threadly.protocols.http.request;

import org.threadly.protocols.http.shared.HTTPConstants;
import org.threadly.protocols.http.shared.HTTPHeaders;

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
  
  @Override
  public String toString() {
    return this.request.toString()+HTTPConstants.HTTP_NEWLINE_DELIMINATOR+
        this.headers.toString()+HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR;
  }
  
  public HTTPRequestBuilder makeBuilder() {
    HTTPRequestBuilder hrb = new HTTPRequestBuilder().setHTTPHeaders(headers).setHTTPRequestHeader(request);
    return hrb;
  }
  
}
