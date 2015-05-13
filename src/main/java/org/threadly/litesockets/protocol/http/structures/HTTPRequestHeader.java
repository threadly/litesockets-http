package org.threadly.litesockets.protocol.http.structures;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.threadly.litesockets.protocol.http.HTTPConstants;
import org.threadly.litesockets.protocol.http.HTTPConstants.REQUEST_TYPE;

public class HTTPRequestHeader {
  public final String rawRequest;
  public final String requestType;
  public final String requestPath;
  public final Map<String, String> requestQuery;
  public final String httpVersion;
  
  public HTTPRequestHeader(String rawRequest) {
    this.rawRequest = rawRequest.intern();
    String[] tmp = rawRequest.split(" ");
    if(tmp.length < 3 || tmp.length > 3) {
      throw new IllegalArgumentException("HTTPRequestHeader can only have 3 arguments! :"+rawRequest);
    }
    requestType = tmp[0].trim().toUpperCase();
    String ptmp = tmp[1].trim();
    if(ptmp.indexOf("?") >= 0) {
      int pos = tmp[1].indexOf("?");
      requestPath = ptmp.substring(0, pos).intern();
      requestQuery = HTTPUtils.parseQueryString(ptmp.substring(pos+1));
    } else {
      requestPath = ptmp.intern();
      requestQuery = Collections.unmodifiableMap(new HashMap<String, String>());
    }
    
    httpVersion = tmp[2].trim().toUpperCase().intern();
    if(!httpVersion.equals(HTTPConstants.HTTP_VERSION_1_1) && !httpVersion.equals(HTTPConstants.HTTP_VERSION_1_0)) {
      throw new IllegalStateException("Unknown HTTP Version!:"+httpVersion);
    }
  }
  
  public HTTPRequestHeader(REQUEST_TYPE requestType, String requestPath, Map<String, String> requestQuery, String httpVersion){
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
    rawRequest = sb.toString();
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
  
  @Override
  public String toString() {
    return this.rawRequest;
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
