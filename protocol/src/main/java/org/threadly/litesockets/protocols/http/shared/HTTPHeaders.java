package org.threadly.litesockets.protocols.http.shared;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.threadly.util.StringUtils;

/**
 * This is an immutable object of http headers.  These are the key/value pairs
 * separated by a colon. 
 */
public class HTTPHeaders {
  private final String rawHeaders;
  private final Map<String, String> headers;
  
  public HTTPHeaders(String headerString) {
    TreeMap<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    if(headerString.endsWith(HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR)) {
      this.rawHeaders = headerString.substring(0, headerString.length()-2);
    } else if(!headerString.endsWith(HTTPConstants.HTTP_NEWLINE_DELIMINATOR)) { 
      this.rawHeaders = (headerString+HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
    } else {
      headerString = HTTPUtils.leftTrim(headerString);
      this.rawHeaders = headerString;
    }
    
    String[] rows = headerString.split(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
    for(String h: rows) {
      if (h.isEmpty()) {
        continue;
      }
      int delim = h.indexOf(':');
      if (delim < 0) {
        throw new IllegalArgumentException("Header is missing key value delim: " + h);
      }
      map.put(h.substring(0, delim).trim(), h.substring(delim + 1).trim());
    }
   
    if (map.isEmpty()) {
      headers = Collections.emptyMap();
    } else {
      headers = Collections.unmodifiableMap(map);
    }
  }
  
  public HTTPHeaders(final Map<String, String> headerMap) {
    TreeMap<String, String> lheaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for(Entry<String, String> kv: headerMap.entrySet()) {
      lheaders.put(kv.getKey().trim(), kv.getValue().trim());
    }
    rawHeaders = formatHeaderMap(lheaders);
    this.headers = Collections.unmodifiableMap(lheaders);
  }

  /**
   * Converts a {@link Map} into the HTTP standard header format.
   * 
   * @param headerMap The map to use for input
   * @return The string of HTTP encoded headers
   */
  public static String formatHeaderMap(Map<String, String> headerMap) {
    StringBuilder sb = new StringBuilder();
    for(Entry<String, String> kv: headerMap.entrySet()) {
      sb.append(kv.getKey());
      sb.append(HTTPConstants.HTTP_HEADER_VALUE_DELIMINATOR);
      sb.append(HTTPConstants.SPACE);
      sb.append(kv.getValue());
      sb.append(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
    }
    return sb.toString();
  }
  
  /**
   * Get the header key / values as a {@link Map}.
   * 
   * @return The header key / values.
   */
  public Map<String, String> getHeadersMap() {
    return headers;
  }
  
  /**
   * Check if the headers are defining this as a chunked request / response.  This is determined 
   * by the presence of a header key {@link HTTPConstants#HTTP_KEY_TRANSFER_ENCODING}.
   * 
   * @return {@code true} if the encoding is set as chunked
   */
  public boolean isChunked() {
    return headers.containsKey(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING);
  }
  
  /**
   * Get the value associated with a key in the headers.
   * 
   * @param header The key to use for the header value
   * @return The header value or {@code null} if no header key match is found
   */
  public String getHeader(String header) {
    return headers.get(header);
  }

  /**
   * Parse out the content length from the value of {@link HTTPConstants#HTTP_KEY_CONTENT_LENGTH} 
   * header.
   * 
   * @return The length sent in the header or {@code -1} if none is provided (or failed to parse)
   */
  public long getContentLength() {
    String scl = headers.get(HTTPConstants.HTTP_KEY_CONTENT_LENGTH);
    long cl = -1;
    if (! StringUtils.isNullOrEmpty(scl)) {
      try {
        cl = Long.parseLong(scl);
      } catch (NumberFormatException e) {
        cl = -1;
      }
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
