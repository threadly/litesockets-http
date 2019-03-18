package org.threadly.litesockets.protocols.http.shared;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.buffers.ReuseableMergedByteBuffers;
import org.threadly.util.StringUtils;

/**
 * Utility functions for working with the HTTP protocol.
 */
public class HTTPUtils {
  /**
   * Trim whitespace from the left side of the String only.
   * 
   * @param value String to trim from
   * @return A left-trimmed string
   */
  public static String leftTrim(String value) {
    int count = 0;
    while(value.length() > count && Character.isWhitespace(value.charAt(count))) {
      count++;
    }
    return value.substring(count);
  }
  
  /**
   * Used for parsing a chunk encoded request / response.  This will find the end of the chunk 
   * and then parse out the size of the next chunk
   * 
   * @param bb Source {@link ByteBuffer} to read from
   * @return The next chunk size or {@code -1} if could not be found or failed to parse
   */
  public static int getNextChunkLength(final ByteBuffer bb) {
    MergedByteBuffers mbb = new ReuseableMergedByteBuffers();
    mbb.add(bb);
    int pos = mbb.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
    try {
      if(pos >= 0) {
        String csize = mbb.getAsString(pos);
        return Integer.parseInt(csize, HTTPConstants.HEX_SIZE);
      }
    } catch(NumberFormatException e) {
      return -1;
    }
    return -1;
  }
  
  /**
   * Wraps the given data in a chunk to be used for chunked encoding.
   * 
   * @param bb The data to wrap
   * @return A new buffer which wraps the data in a chunk encoded segment
   */
  public static ByteBuffer wrapInChunk(ByteBuffer bb) {
    byte[] size = Integer.toHexString(bb.remaining()).getBytes();
    ByteBuffer newBB = ByteBuffer.allocate(bb.remaining()+
        size.length+HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length()+HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length());
    newBB.put(size);
    newBB.put(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.getBytes());
    newBB.put(bb);
    newBB.put(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.getBytes());
    newBB.flip();
    return newBB;
  }
  
  /**
   * Converts query parameters stored in a map to a {@link String} that can be added to the URI.
   * 
   * @param map Map to source the values from
   * @return HTTP standard query parameters, prefixed with {@code ?}
   */
  public static String queryToString(Map<String, List<String>> map) {
    if(map.isEmpty()) {
      return "";
    }
    
    StringBuilder sb = new StringBuilder();
    sb.append('?');
    for(Map.Entry<String, List<String>> e : map.entrySet()) {
      if (e.getValue() == null || e.getValue().isEmpty()) {
        if(sb.length() > 1) {
          sb.append('&');  
        }
        sb.append(e.getKey());
      } else {
        for (String v : e.getValue()) {
          if(sb.length() > 1) {
            sb.append('&');  
          }
          sb.append(e.getKey());
          if(! StringUtils.isNullOrEmpty(v)) {
            sb.append('=');
            sb.append(v);
          }
        }
      }
    }
    return sb.toString();
  }
  
  /**
   * Parses http standard query parameters from the URL into a {@link Map} representation.
   * 
   * @param query The String to parse
   * @return The parsed our parameters into a {@link Map}
   */
  public static Map<String, List<String>> queryToMap(String query) {
    if (StringUtils.isNullOrEmpty(query)) {
      return Collections.emptyMap();
    }
    Map<String, List<String>> map = new HashMap<>();
    if(query.startsWith("?")) {
      query = query.substring(1);
    }
    int qpos = query.indexOf("?");
    if (qpos >= 0){
      query = query.substring(qpos+1);
    }
    String[] tmpQ = query.trim().split("&");
    for(String kv: tmpQ) {
      String[] tmpkv = kv.split("=");
      if (tmpkv.length == 0) {
        // case where either no `=` or empty key string
        continue;
      }
      List<String> paramValues = map.computeIfAbsent(tmpkv[0], (ignored) -> new ArrayList<>(2));
      if(tmpkv.length == 1) {
        paramValues.add("");
      } else {
        paramValues.add(tmpkv[1].trim());
      }
    }
    return Collections.unmodifiableMap(map);
  }
}
