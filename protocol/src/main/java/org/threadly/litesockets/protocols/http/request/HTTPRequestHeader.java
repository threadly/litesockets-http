package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPRequestType;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;

/**
 * This is an immutable HTTP Request Header.  Basically the first line of the http request.  
 */
public class HTTPRequestHeader {
  private static final int REQUIRED_REQUEST_ITEMS = 3;
  private final String rawRequest;
  private final String requestType;
  private final String requestPath;
  private final Map<String, String> requestQuery;
  private final String httpVersion;
  
  public HTTPRequestHeader(final String requestHeader) {
    this.rawRequest = requestHeader.trim();
    String[] tmp = rawRequest.split(" ");
    if(tmp.length != REQUIRED_REQUEST_ITEMS) {
      throw new IllegalArgumentException("HTTPRequestHeader can only have 3 arguments! :"+requestHeader);
    }
    requestType = tmp[0].trim().toUpperCase();
    String ptmp = tmp[1].trim();
    int queryParamPos = ptmp.indexOf('?');
    if(queryParamPos >= 0) {
      requestPath = ptmp.substring(0, queryParamPos);
      requestQuery = HTTPUtils.queryToMap(ptmp.substring(queryParamPos+1));
    } else {
      requestPath = ptmp;
      requestQuery = Collections.emptyMap();
    }
    
    httpVersion = tmp[2].trim().toUpperCase();
    if(!httpVersion.equals(HTTPConstants.HTTP_VERSION_1_1) && !httpVersion.equals(HTTPConstants.HTTP_VERSION_1_0)) {
      throw new UnsupportedOperationException("Unknown HTTP Version!:"+httpVersion);
    }
  }
  
  public HTTPRequestHeader(HTTPRequestType requestType, String requestPath, Map<String, String> requestQuery, String httpVersion){
    this(requestType.toString(), requestPath, requestQuery, httpVersion);
  }
  
  public HTTPRequestHeader(String requestType, String requestPath, Map<String, String> requestQuery, String httpVersion){
    this.requestType = requestType;
    final HashMap<String, String> rqm = new HashMap<>();
    int queryParamPos = requestPath.indexOf("?");
    if(queryParamPos >= 0) {
      this.requestPath = requestPath.substring(0, queryParamPos);
      rqm.putAll(HTTPUtils.queryToMap(requestPath.substring(queryParamPos+1)));
    } else {
      this.requestPath = requestPath;
    }
    if(requestQuery != null) {
      rqm.putAll(requestQuery);
    }
    this.requestQuery = Collections.unmodifiableMap(rqm);
    if(!HTTPConstants.HTTP_VERSION_1_1.equals(httpVersion) && !HTTPConstants.HTTP_VERSION_1_0.equals(httpVersion)) {
      throw new UnsupportedOperationException("Unknown HTTP Version!:"+httpVersion);
    }
    this.httpVersion = httpVersion.trim().toUpperCase();
    StringBuilder sb = new StringBuilder();
    sb.append(requestType.toString());
    sb.append(HTTPConstants.SPACE);
    sb.append(requestPath);
    if(requestQuery != null && ! requestQuery.isEmpty()) {
      sb.append(HTTPUtils.queryToString(requestQuery));
    }
    sb.append(HTTPConstants.SPACE);
    sb.append(this.httpVersion);
    rawRequest = sb.toString();
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
    return ByteBuffer.wrap(rawRequest.getBytes()).asReadOnlyBuffer();
  }
  
  public int length() {
    return rawRequest.length();
  }
  
  @Override
  public String toString() {
    return rawRequest;
  }
  
  @Override
  public int hashCode() {
    return this.rawRequest.hashCode();
  }
  
  @Override
  public boolean equals(Object o) {
    if(o == this) {
      return true;
    } else if(o instanceof HTTPRequestHeader) {
      HTTPRequestHeader hrh = (HTTPRequestHeader)o;
      if(hrh.toString().equals(this.toString())) {
        return true;
      }
    }
    return false;
  }
}
