package org.threadly.litesockets.protocols.http.shared;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * This is an immutable object of http headers.  These are the key/value pairs
 * separated by a colon. 
 */
public class HTTPHeaders {
  private final String rawHeaders;
  private final Map<String, String> headers;
  
  public HTTPHeaders(String headerString) {
    TreeMap<String, String> map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    if(headerString.endsWith(HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR)) {
      this.rawHeaders = headerString.substring(0, headerString.length()-2);
    } else if(!headerString.endsWith(HTTPConstants.HTTP_NEWLINE_DELIMINATOR)) { 
      this.rawHeaders = (headerString+HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
    } else {
      headerString = HTTPUtils.leftTrim(headerString);
      this.rawHeaders = headerString;
    }
    
    String[] rows = headerString.trim().split(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
    for(String h: rows) {
      String[] kv = h.split(HTTPConstants.HTTP_HEADER_VALUE_DELIMINATOR);
      String key = kv[0].trim();
      String value = kv[1].trim();
      map.put(key, value);
    }
    
    headers = Collections.unmodifiableMap(map);
  }
  
  public HTTPHeaders(final Map<String, String> headerMap) {
    TreeMap<String, String> lheaders = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    StringBuilder sb = new StringBuilder();
    for(Entry<String, String> kv: headerMap.entrySet()) {
      lheaders.put(kv.getKey().trim(), kv.getValue().trim());
      sb.append(kv.getKey());
      sb.append(HTTPConstants.HTTP_HEADER_VALUE_DELIMINATOR);
      sb.append(HTTPConstants.SPACE);
      sb.append(kv.getValue());
      sb.append(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
    }
    rawHeaders = sb.toString();
    this.headers = Collections.unmodifiableMap(lheaders);
  }
  
  public Map<String, String> getHeadersMap() {
    return headers;
  }
  
  public boolean isChunked() {
    return headers.containsKey(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING);
  }
  
  public String getHeader(String header) {
    return headers.get(header);
  }

  public int getContentLength() {
    String scl = headers.get(HTTPConstants.HTTP_KEY_CONTENT_LENGTH);
    int cl = -1;
    try {
      if(scl != null) {
        cl =  Integer.parseInt(scl);
      }
    } catch(Exception e) {
      cl = -1;
    }
    return cl;
  }
  
  @Override
  public String toString() {
    return rawHeaders;
  }
  
  @Override
  public int hashCode() {
    return rawHeaders.hashCode();
  }
  
  @Override
  public boolean equals(Object o) {
    if(o == this) {
      return true;
    } else if(o instanceof HTTPHeaders) {
      HTTPHeaders h = (HTTPHeaders)o;
      return headers.equals(h.headers);
    }
    return false;
  }
}
