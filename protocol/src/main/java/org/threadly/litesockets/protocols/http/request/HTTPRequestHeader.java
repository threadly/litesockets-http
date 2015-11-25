package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.RequestType;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;


/**
 * This is an immutable HTTP Request Header.  Basically the first line of the http request.  
 */
public class HTTPRequestHeader {
  public static final int REQUIRED_REQUEST_ITEMS = 3;
  public final byte[] rawRequest;
  public final String requestType;
  public final String requestPath;
  public final Map<String, String> requestQuery;
  public final String httpVersion;
  
  public HTTPRequestHeader(final String requestHeader) {
    this.rawRequest = requestHeader.trim().getBytes();
    String[] tmp = requestHeader.trim().split(" ");
    if(tmp.length != REQUIRED_REQUEST_ITEMS) {
      throw new IllegalArgumentException("HTTPRequestHeader can only have 3 arguments! :"+requestHeader);
    }
    requestType = tmp[0].trim().toUpperCase();
    String ptmp = tmp[1].trim();
    if(ptmp.indexOf("?") >= 0) {
      int pos = tmp[1].indexOf("?");
      requestPath = ptmp.substring(0, pos).intern();
      requestQuery = HTTPUtils.queryToMap(ptmp.substring(pos+1));
    } else {
      requestPath = ptmp.intern();
      requestQuery = HTTPUtils.queryToMap("");
    }
    
    httpVersion = tmp[2].trim().toUpperCase().intern();
    if(!httpVersion.equals(HTTPConstants.HTTP_VERSION_1_1) && !httpVersion.equals(HTTPConstants.HTTP_VERSION_1_0)) {
      throw new IllegalStateException("Unknown HTTP Version!:"+httpVersion);
    }
  }
  
  public HTTPRequestHeader(RequestType requestType, String requestPath, Map<String, String> requestQuery, String httpVersion){
    this(requestType.toString(), requestPath, requestQuery, httpVersion);
  }
  
  public HTTPRequestHeader(String requestType, String requestPath, Map<String, String> requestQuery, String httpVersion){
    this.requestType = requestType;
    this.requestPath = requestPath.intern();
    if(requestQuery == null) {
      this.requestQuery = Collections.unmodifiableMap(new HashMap<String, String>());
    } else {
      this.requestQuery = Collections.unmodifiableMap(requestQuery);
    }
    this.httpVersion = httpVersion.toUpperCase().intern();
    StringBuilder sb = new StringBuilder();
    sb.append(requestType.toString());
    sb.append(HTTPConstants.SPACE);
    sb.append(requestPath);
    if(requestQuery != null && requestQuery.size() > 0) {
      sb.append(HTTPUtils.queryToString(requestQuery));
    }
    sb.append(HTTPConstants.SPACE);
    sb.append(httpVersion);
    rawRequest = sb.toString().getBytes();
    if(!httpVersion.equals(HTTPConstants.HTTP_VERSION_1_1) && !httpVersion.equals(HTTPConstants.HTTP_VERSION_1_0)) {
      throw new IllegalStateException("Unknown HTTP Version!:"+httpVersion);
    }
  }
  
  public String getRequestType() {
    return requestType;
  }
  
  public String getRequestPath() {
    return requestPath;
  }
  
  public Map<String, String> getRequestQuery() {
    return requestQuery;
  }
  
  public String getHttpVersion() {
    return httpVersion;
  }
  
  public ByteBuffer getByteBuffer() {
    return ByteBuffer.wrap(rawRequest).asReadOnlyBuffer();
  }
  
  @Override
  public String toString() {
    return new String(rawRequest);
  }
  
  @Override
  public int hashCode() {
    return this.rawRequest.hashCode();
  }
  
  @Override
  public boolean equals(Object o) {
    if(o instanceof HTTPRequestHeader) {
      HTTPRequestHeader hrh = (HTTPRequestHeader)o;
      if(hrh.toString().equals(this.toString())) {
        return true;
      }
    }
    return false;
  }
  
}
