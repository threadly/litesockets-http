package org.threadly.litesockets.protocol.http.structures;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.threadly.litesockets.protocol.http.HTTPConstants;

public class HTTPHeaders {
  public final String rawHeaders;
  public final Map<String, String> headers;
  
  public HTTPHeaders(String rawHeaders) {
    HashMap<String, String> map = new HashMap<String, String>();
    this.rawHeaders = rawHeaders.intern();
    String[] rows = rawHeaders.split(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
    for(String h: rows) {
      String[] kv = h.split(HTTPConstants.HTTP_HEADER_VALUE_DELIMINATOR);
      String key = kv[0].toLowerCase().intern();
      String value = kv[1].trim().intern();
      map.put(key, value);
    }
    headers = Collections.unmodifiableMap(map);
  }
  
  public HTTPHeaders(Map<String, String> headers) {
    this.headers = Collections.unmodifiableMap(headers);
    StringBuilder sb = new StringBuilder();
    int count = 0;
    for(Entry<String, String> kv: headers.entrySet()) {
      count++;
      sb.append(kv.getKey());
      sb.append(HTTPConstants.HTTP_HEADER_VALUE_DELIMINATOR);
      sb.append(HTTPConstants.SPACE);
      sb.append(kv.getValue());
      if(count < headers.size()) {
        sb.append(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
      }
    }
    rawHeaders = sb.toString().intern();
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
    if(o instanceof HTTPHeaders) {
      HTTPHeaders h = (HTTPHeaders)o;
      if(h == this) {
        return true;
      }
      if(h.headers.size() == this.headers.size()) {
        for(String key: h.headers.keySet()) {
          if(this.headers.get(key) != h.headers.get(key)) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }
}
