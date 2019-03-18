package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPRequestMethod;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;

/**
 * This is an immutable HTTP Request Header.  Basically the first line of the http request.  
 * 
 * This is the first line of an http request formated with spaces "RequestMethod path?query version".
 */
public class HTTPRequestHeader {
  private static final int REQUIRED_REQUEST_ITEMS = 3;
  private final String rawRequest;
  private final String requestMethod;
  private final String requestPath;
  private final Map<String, List<String>> requestQuery;
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
    requestMethod = tmp[0].trim().toUpperCase();
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
   * @param requestMethod the {@link HTTPRequestMethod} to set.
   * @param requestPath the {@link HTTPRequestMethod} to set.
   * @param requestQuery the query to set.
   * @param httpVersion the httpVersion to set.
   */
  public HTTPRequestHeader(HTTPRequestMethod requestMethod, String requestPath, 
                           Map<String, List<String>> requestQuery, String httpVersion){
    this(requestMethod.toString(), requestPath, requestQuery, httpVersion);
  }
  
  /**
   * Creates a new Immutable {@link HTTPRequest} object from the parts that are in a request.  
   * This can take non-standard request methods.
   * 
   * @param requestMethod the {@link HTTPRequestMethod} to set.
   * @param requestPath the {@link HTTPRequestMethod} to set.
   * @param requestQuery the query to set.
   * @param httpVersion the httpVersion to set.
   */
  public HTTPRequestHeader(String requestMethod, String requestPath, 
                           Map<String, List<String>> requestQuery, String httpVersion){ // TODO
    this.requestMethod = requestMethod;
    final LinkedHashMap<String, List<String>> rqm = new LinkedHashMap<>();
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
    if(!HTTPConstants.HTTP_VERSION_1_1.equals(httpVersion) && 
       !HTTPConstants.HTTP_VERSION_1_0.equals(httpVersion)) {
      throw new UnsupportedOperationException("Unknown HTTP Version!:"+httpVersion);
    }
    this.httpVersion = httpVersion.trim().toUpperCase();
    StringBuilder sb = new StringBuilder();
    sb.append(requestMethod.toString());
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
   * Gets the http request method (ie POST, GET, HEAD, etc).
   * 
   * @return the request method.
   */
  public String getRequestMethod() {
    return requestMethod;
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
  public Map<String, List<String>> getRequestQuery() {
    return requestQuery;
  }
  
  /**
   * Gets the value to a given query parameter.  This will throw an exception if there is multiple 
   * values associated to the key.
   *  
   * @param paramKey The key associated with the query parameter value
   * @return the request parameter value or {@code null} if none is associated
   */
  public String getRequestQueryValue(String paramKey) {
    List<String> values = requestQuery.get(paramKey);
    if (values == null || values.isEmpty()) {
      return null;
    } else if (values.size() > 1) {
      throw new IllegalStateException("Multiple values for parameter: " + paramKey);
    } else {
      return values.get(0);
    }
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
