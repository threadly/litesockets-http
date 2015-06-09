package org.threadly.litesockets.protocol.http.structures;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.threadly.litesockets.protocol.http.structures.HTTPConstants.REQUEST_TYPE;

public class HTTPUtils {
  public static final HTTPHeaders DEFAULT_HEADERS= new HTTPHeaders(HTTPConstants.DEFAULT_HEADERS_MAP);
  public static final HTTPRequestHeader DEFAULT_REQUEST_HEADER= new HTTPRequestHeader(REQUEST_TYPE.GET, "/", null, HTTPConstants.HTTP_VERSION_1_1);
  
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
  
  public static Map<String, String> parseQueryString(String query) {
    Map<String, String> map = new HashMap<String, String>();
    if(query.startsWith("?")) {
      query = query.substring(1);
    }
    if(!query.equals("")){
      String[] tmpQ = query.trim().split("&");
      for(String kv: tmpQ) {
        String[] tmpkv = kv.split("=");
        if(tmpkv.length == 1) {
          map.put(tmpkv[0].intern(), "");
        } else {
          map.put(tmpkv[0].intern(), tmpkv[1].intern());
        }
      }
    }
    return map;
  }

}
