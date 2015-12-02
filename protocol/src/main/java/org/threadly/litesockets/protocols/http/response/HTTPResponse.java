package org.threadly.litesockets.protocols.http.response;

import java.util.Map;

import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;


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

  public HTTPResponse(String rCode, String httpVersion, Map<String, String> headers) {
    rHeader = new HTTPResponseHeader(rCode, httpVersion);
    this.headers = new HTTPHeaders(headers);
  }
  
  public String getResponseCode() {
    if(rHeader != null) {
      return rHeader.getResponseCode();
    }
    return "-1";
  }
  
  public HTTPHeaders getHeaders() {
    return headers;
  }
  
  @Override
  public boolean equals(Object o) {
    if(o instanceof HTTPRequest) {
      HTTPResponse hr = (HTTPResponse)o;
      if(hr.rHeader.equals(rHeader) && hr.headers.equals(headers)) {
        return true;
      }
    }
    return false;
  }
  
  @Override
  public String toString() {
    return this.rHeader+HTTPConstants.HTTP_NEWLINE_DELIMINATOR+headers+HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR;
  }
  
  public HTTPResponseBuilder makeBuilder() {
    return new HTTPResponseBuilder().setResponseHeader(rHeader).setHeaders(headers);
  }

}
