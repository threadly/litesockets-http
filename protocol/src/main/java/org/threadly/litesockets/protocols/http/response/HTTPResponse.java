package org.threadly.protocols.http.response;

import java.nio.ByteBuffer;
import java.util.Map;

import org.threadly.protocols.http.shared.HTTPHeaders;


/**
 *  An Immutable HTTPResponse object.  This contains all information from an HTTP Response.
 *  
 *   NOTE: builder needed.
 */
public class HTTPResponse {
  private final HTTPResponseHeader rHeader;
  private final HTTPHeaders headers;
  private final int bodyLength;
  private final byte[] body;
  private final ByteBuffer bodybb;
  private final Throwable error;
  
  public HTTPResponse(HTTPResponseHeader rHeader, HTTPHeaders headers, byte[] body) {
    this.rHeader = rHeader;
    this.headers = headers;
    this.bodyLength = body.length;
    this.body = body;
    bodybb = ByteBuffer.wrap(body).asReadOnlyBuffer();
    error = null;
  }

  public HTTPResponse(String rCode, String httpVersion, Map<String, String> headers, byte[] body) {
    rHeader = new HTTPResponseHeader(rCode, httpVersion);
    this.headers = new HTTPHeaders(headers);
    this.bodyLength = body.length;
    this.body = body;
    bodybb = ByteBuffer.wrap(body).asReadOnlyBuffer();
    error = null;
  }
  
  public HTTPResponse(Throwable t) {
    rHeader = null;
    this.headers = null;
    this.bodyLength = 0;
    this.body = new byte[0];
    bodybb = null;
    error = t;
  }
  
  public String getResponseCode() {
    if(rHeader != null) {
      return rHeader.getResponseCode();
    }
    return "-1";
  }
  
  public Map<String, String> getHeaders() {
    return headers.headers;
  }
  
  public String getHttpVersion() {
    if(rHeader != null) {
      return rHeader.getResponseCode();
    }
    return "";
  }
  
  public int getBodyLength() {
    return bodyLength;
  }
  
  public boolean hasError() {
    if(error == null) {
      return false;
    }
    return true;
  }
  
  public Throwable getError() {
    return error;
  }
  
  public String getHeader(String header) {
    return headers.getHeader(header.toLowerCase());
  }
  
  public ByteBuffer getBody() {
    return bodybb.duplicate();
  }
  
  public String getBodyAsString() {
    return new String(body);
  }
  
  public String getHeadersAsString() {
    return new String(body);
  }

}
