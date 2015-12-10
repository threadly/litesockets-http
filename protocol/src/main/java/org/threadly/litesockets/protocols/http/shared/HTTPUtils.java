package org.threadly.litesockets.protocols.http.shared;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.threadly.litesockets.utils.MergedByteBuffers;

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
  
  public static String bbToString(ByteBuffer bb) {
    byte[] ba = new byte[bb.remaining()];
    bb.get(ba);
    return new String(ba);
  }
  
  public static int getNextChunkLength(final ByteBuffer bb) {
    MergedByteBuffers mbb = new MergedByteBuffers();
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
  
  public static byte[] wrapInChunk(byte[] ba) {
    return wrapInChunk(ByteBuffer.wrap(ba)).array();
  }
  
  public static String queryToString(Map<String,String> map) {
    if(map.size() > 0) {
      StringBuilder sb = new StringBuilder();
      sb.append("?");
      int count = 0;
      for(String k: map.keySet()) {
        if(count > 0) {
          sb.append("&");  
        }
        sb.append(k);
        String v = map.get(k);
        if(v != null && ! v.equals("")) {
          sb.append("=");
          sb.append(v);
        }
        count++;
      }
      return sb.toString();
    }
    return "";
  }
  
  public static Map<String, String> queryToMap(String query) {
    Map<String, String> map = new HashMap<String, String>();
    if(query.startsWith("?")) {
      query = query.substring(1);
    }
    if(!query.equals("")){
      String[] tmpQ = query.trim().split("&");
      for(String kv: tmpQ) {
        String[] tmpkv = kv.split("=");
        if(tmpkv.length == 1) {
          map.put(tmpkv[0].trim().intern(), "");
        } else {
          map.put(tmpkv[0].trim().intern(), tmpkv[1].trim().intern());
        }
      }
    }
    return Collections.unmodifiableMap(map);
  }

}
