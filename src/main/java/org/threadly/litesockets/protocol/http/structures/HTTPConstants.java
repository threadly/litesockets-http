package org.threadly.litesockets.protocol.http.structures;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a bunch of constants used by the HTTP protocol.
 */
public class HTTPConstants {
  /**
   * These are the different protocol types, currently only http and https.
   */
  public static enum PROTOCOL {
    HTTP, HTTPS
  }
  
  /**
   * Enum of the normal HTTP Request Types.
   */
  public static enum REQUEST_TYPE {
    OPTIONS, GET, HEAD, POST, PUT, DELETE, TRACE, CONNECT
  }
  
  public static final String HTTP_DOUBLE_NEWLINE_DELIMINATOR = "\r\n\r\n";
  public static final String HTTP_NEWLINE_DELIMINATOR = "\r\n";
  public static final String HTTP_HEADER_VALUE_DELIMINATOR = ":";
  public static final String SPACE = " ";
  
  public static final String HTTP_KEY_CONNECTION = "connection";
  public static final String HTTP_KEY_CONTENT_TYPE = "content-type";
  public static final String HTTP_KEY_CONTENT_LENGTH = "content-length";
  public static final String HTTP_KEY_TRANSFER_ENCODING  = "transfer-encoding";
  public static final String HTTP_KEY_AUTHORIZATION = "authorization";
  public static final String HTTP_KEY_USER_AGENT = "user-agent";
  public static final String HTTP_KEY_ACCEPT = "accept";
  public static final String HTTP_KEY_HOST = "host";
  public static final String HTTP_VERSION_1_1 = "HTTP/1.1";
  public static final String HTTP_VERSION_1_0 = "HTTP/1.0";
  
  public static final int DEFAULT_READ_TIMEOUT = 30000;
  
  public static final Map<String, String> DEFAULT_HEADERS_MAP;
  public static final Map<String, String> RESPONSE_CODES;
  static {
    HashMap<String, String> dh = new HashMap<String, String>();
    // header keys should always be lower case
    dh.put(HTTP_KEY_USER_AGENT, "litesockets");
    dh.put(HTTP_KEY_ACCEPT, "*/*");
    DEFAULT_HEADERS_MAP = Collections.unmodifiableMap(dh);
    
    HashMap<String, String> rt = new HashMap<String, String>();
    rt.put("100", "Continue");
    rt.put("101", "Switching Protocols");
    rt.put("200", "OK");
    rt.put("201", "Created");
    rt.put("202", "Accepted");
    rt.put("203", "Non-Authoritative Information");
    rt.put("204", "No Content");
    rt.put("205", "Reset Content");
    rt.put("206", "Partial Content");
    rt.put("300", "Multiple Choices");
    rt.put("301", "Moved Permanently");
    rt.put("302", "Found");
    rt.put("303", "See Other");
    rt.put("304", "Not Modified");
    rt.put("305", "(Unused)");
    rt.put("306", "Temporary Redirect");
    rt.put("400", "Bad Request");
    rt.put("401", "Unauthorized");
    rt.put("402", "Payment Required");
    rt.put("403", "Forbidden");
    rt.put("404", "Not Found");
    rt.put("405", "Method Not Allowed");
    rt.put("406", "Not Acceptable");
    rt.put("407", "Proxy Authentication Required");
    rt.put("408", "Request Timeout");
    rt.put("409", "Conflict");
    rt.put("410", "Gone");
    rt.put("411", "Length Required");
    rt.put("412", "Precondition Failed");
    rt.put("413", "Request Entity Too Large");
    rt.put("414", "Request-URI Too Long");
    rt.put("415", "Unsupported Media Type");
    rt.put("416", "Requested Range Not Satisfiable");
    rt.put("417", "Expectation Failed");
    rt.put("500", "Internal Server Error");
    rt.put("501", "Not Implemented");
    rt.put("502", "Bad Gateway");
    rt.put("503", "Service Unavailable");
    rt.put("504", "Gateway Timeout");
    rt.put("505", "HTTP Version Not Supported");
    
    RESPONSE_CODES = Collections.unmodifiableMap(rt);
    
  }

}
