package org.threadly.litesockets.protocols.http.request;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.threadly.litesockets.protocols.http.shared.HTTPAddress;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;
import org.threadly.litesockets.protocols.http.shared.RequestType;

/**
 * A builder object for HTTPRequests.  This helps construct different types of httpRequests. 
 *
 */
public class HTTPRequestBuilder {
  private final Map<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
  private HTTPRequestHeader request = HTTPUtils.DEFAULT_REQUEST_HEADER;
  private String host = "localhost";
  private int port = 80;
  
  public HTTPRequestBuilder(){
    headers.putAll(HTTPConstants.DEFAULT_HEADERS_MAP);
    setHeader(HTTPConstants.HTTP_KEY_HOST, host);
  }
  
  public HTTPRequestBuilder(URL url) {
    headers.putAll(HTTPConstants.DEFAULT_HEADERS_MAP);
    setURL(url);
  }
  
  public HTTPRequestBuilder setURL(URL url) {
    
    host = url.getHost();
    port = url.getPort();
    if(port <= 0) {
      port = url.getDefaultPort();
    }

    String tmpPath =  url.getPath();
    if(tmpPath == null || tmpPath.equals("")) {
      tmpPath = "/";
    }
    String q = url.getQuery();
    if(q != null) {
      request = new HTTPRequestHeader(request.getRequestType(), tmpPath, HTTPUtils.queryToMap(q), request.getHttpVersion());
    } else {
      request = new HTTPRequestHeader(request.getRequestType(), tmpPath, null, request.getHttpVersion());
    }
    setHeader(HTTPConstants.HTTP_KEY_HOST, host);
    return this;
  }
  
  public HTTPRequestBuilder setHTTPRequestHeader(HTTPRequestHeader hrh) {
    request = hrh;
    return this;
  }
  
  public HTTPRequestBuilder setHTTPHeaders(HTTPHeaders hh) {
    this.headers.clear();
    for(Entry<String, String> head: hh.getHeadersMap().entrySet()) {
      setHeader(head.getKey(), head.getValue());
    }
    return this;
  }
  
  public HTTPRequestBuilder setHTTPAddress(HTTPAddress ha) {
    this.host = ha.getHost();
    this.port = ha.getPort();
    return this;
  }
  
  public HTTPRequestBuilder duplicate() {
    HTTPRequestBuilder hrb = new HTTPRequestBuilder();
    hrb.request = request;
    for(Entry<String, String> entry: headers.entrySet()) {
      hrb.setHeader(entry.getKey(), entry.getValue());
    }
    return hrb;
  }
  
  public HTTPRequestBuilder setHost(String host) {
    this.host = host;
    setHeader(HTTPConstants.HTTP_KEY_HOST, host);
    return this;
  }
  
  public HTTPRequestBuilder setPort(int port) {
    if(port < 1 || port > Short.MAX_VALUE*2) {
      throw new IllegalArgumentException("Not a valid port number: "+port);
    }
    this.port = port;
    return this;
  }
  
  public HTTPRequestBuilder setPath(String path) {
    this.request = new HTTPRequestHeader(request.getRequestType(), path, request.getRequestQuery(), request.getHttpVersion());
    return this;
  }
  
  public HTTPRequestBuilder setQueryString(String query) {
    this.request = new HTTPRequestHeader(request.getRequestType(), request.getRequestPath(), HTTPUtils.queryToMap(query), request.getHttpVersion());
    return this;
  }
  
  public HTTPRequestBuilder appedQuery(String key, String value) {
    HashMap<String, String> map = new HashMap<String, String>(request.getRequestQuery());
    map.put(key, value);
    this.request = new HTTPRequestHeader(request.getRequestType(), request.getRequestPath(), map, request.getHttpVersion());
    return this;
  }
  
  public HTTPRequestBuilder removeQuery(String key) {
    HashMap<String, String> map = new HashMap<String, String>(request.getRequestQuery());
    map.remove(key);
    this.request = new HTTPRequestHeader(request.getRequestType(), request.getRequestPath(), map, request.getHttpVersion());
    return this;
  }
  
  public HTTPRequestBuilder setHeader(String key, String value) {
    headers.put(key, value);
    return this;
  }
  
  public HTTPRequestBuilder removeHeader(String key) {
    headers.remove(key);
    return this;
  }
  
  public HTTPRequestBuilder setRequestType(String rt) {
    this.request = new HTTPRequestHeader(rt, request.getRequestPath(), request.getRequestQuery(), request.getHttpVersion());
    return this;
  }
  
  public HTTPRequestBuilder setRequestType(RequestType rt) {
    this.request = new HTTPRequestHeader(rt, request.getRequestPath(), request.getRequestQuery(), request.getHttpVersion());
    return this;
  }
  
  public HTTPRequest build() {
    return new HTTPRequest(request, new HTTPHeaders(headers));
  }
}

