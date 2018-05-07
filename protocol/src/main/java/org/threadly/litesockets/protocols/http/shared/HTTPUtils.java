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
 * 
 * 
 * @author lwahlmeier
 *
 */
public class HTTPUtils {
  public static String leftTrim(String value) {
    int count = 0;
    while(Character.isWhitespace(value.charAt(count))) {
      count++;
    }
    return value.substring(count);
  }
  
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
  
  public static Map<String, List<String>> queryToMap(String query) {
    if (StringUtils.isNullOrEmpty(query)) {
      return Collections.emptyMap();
    }
    Map<String, List<String>> map = new HashMap<>();
    if(query.startsWith("?")) {
      query = query.substring(1);
    } else if (query.contains("?")){
      int qpos = query.indexOf("?");
      query = query.substring(qpos+1);
    }
    String[] tmpQ = query.trim().split("&");
    for(String kv: tmpQ) {
      String[] tmpkv = kv.split("=");
      if (tmpkv.length == 0) {
        // case where either no `=` or empty key string
        continue;
      }
      List<String> paramValues = map.computeIfAbsent(tmpkv[0], (ignored) -> new ArrayList<>(1));
      if(tmpkv.length == 1) {
        paramValues.add("");
      } else {
        paramValues.add(tmpkv[1].trim());
      }
    }
    return Collections.unmodifiableMap(map);
  }
}
