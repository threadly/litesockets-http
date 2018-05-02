package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPRequestType;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;

/**
 * This is an immutable HTTP Request Header.  Basically the first line of the http request.  
 * 
 * This is the first line of an http request formated with spaces "RequestType path?query version".
 */
public class HTTPRequestHeader {
  private static final int REQUIRED_REQUEST_ITEMS = 3;
  private final String rawRequest;
  private final String requestType;
  private final String requestPath;
  private final Map<String, String> requestQuery;
  private final String httpVersion;
  
  /**
   * This parses an http request string and creates an Immutable {@link HTTPRequest} object for it.
   * 
   * @param requestHeader the string to parse into a {@link HTTPRequest} .
   * @throws IllegalArgumentException If the header fails to parse.
   */
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
  
  /**
   * Creates a new Immutable {@link HTTPRequest} object from the parts that are in a request.
   * 
   * @param requestType the {@link HTTPRequestType} to set.
   * @param requestPath the {@link HTTPRequestType} to set.
   * @param requestQuery the query to set.
   * @param httpVersion the httpVersion to set.
   */
  public HTTPRequestHeader(HTTPRequestType requestType, String requestPath, Map<String, String> requestQuery, String httpVersion){
    this(requestType.toString(), requestPath, requestQuery, httpVersion);
  }
  
  /**
   * Creates a new Immutable {@link HTTPRequest} object from the parts that are in a request.  This can take non-standard RequestTypes
   * 
   * @param requestType the {@link HTTPRequestType} to set.
   * @param requestPath the {@link HTTPRequestType} to set.
   * @param requestQuery the query to set.
   * @param httpVersion the httpVersion to set.
   */
  public HTTPRequestHeader(String requestType, String requestPath, Map<String, String> requestQuery, String httpVersion){
    this.requestType = requestType;
    final LinkedHashMap<String, String> rqm = new LinkedHashMap<>();
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
    if (rqm.isEmpty()) {
      this.requestQuery = Collections.emptyMap();
    } else {
      this.requestQuery = Collections.unmodifiableMap(rqm);
    }
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
  
  /**
   * Gets the requestType set in this request.
   * 
   * @return the request type.
   */
  public String getRequestType() {  // TODO - rename to method?
    return requestType;
  }
  
  /**
   * Gets the requestPath set in this request.  This does not include the query portion.
   * 
   * @return the request path.
   */
  public String getRequestPath() {
    return requestPath;
  }
  
  /**
   * Gets the request query.
   *  
   * @return the request query.
   */
  public Map<String, String> getRequestQuery() {
    return requestQuery;
  }
  
  /**
   * Gets the http version.
   * 
   * @return the http version.
   */
  public String getHttpVersion() {
    return httpVersion;
  }
  
  /**
   * Returns the header as a read-only {@link ByteBuffer}.
   * 
   * The newline/carriage return is not included!
   * 
   * @return a {@link ByteBuffer} of the request header.
   */
  public ByteBuffer getByteBuffer() {
    return ByteBuffer.wrap(rawRequest.getBytes()).asReadOnlyBuffer();
  }
  
  /**
   * The length in bytes of the http request header.
   * 
   * @return length in bytes of the http request header.
   */
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
