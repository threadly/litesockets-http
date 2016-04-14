package org.threadly.litesockets.protocols.http.shared;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.threadly.litesockets.protocols.http.request.HTTPRequestHeader;
import org.threadly.litesockets.protocols.http.response.HTTPResponseHeader;

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
  
  public static final int HEX_SIZE = 16;
  
  public static final String HTTP_DOUBLE_NEWLINE_DELIMINATOR = "\r\n\r\n";
  public static final String HTTP_NEWLINE_DELIMINATOR = "\r\n";
  public static final String HTTP_HEADER_VALUE_DELIMINATOR = ":";
  public static final String SPACE = " ";
  
  public static final String HTTP_KEY_CONNECTION = "Connection";
  public static final String HTTP_KEY_UPGRADE = "Upgrade";
  public static final String HTTP_KEY_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
  public static final String HTTP_KEY_WEBSOCKET_KEY = "Sec-WebSocket-Key";
  public static final String HTTP_KEY_CONTENT_TYPE = "Content-Type";
  public static final String HTTP_KEY_CONTENT_LENGTH = "Content-Length";
  public static final String HTTP_KEY_TRANSFER_ENCODING  = "Transfer-Encoding";
  public static final String HTTP_KEY_AUTHORIZATION = "Authorization";
  public static final String HTTP_KEY_USER_AGENT = "User-Agent";
  public static final String HTTP_KEY_KEEP_ALIVE = "Keep-Alive";
  public static final String HTTP_KEY_ACCEPT = "Accept";
  public static final String HTTP_KEY_HOST = "Host";
  public static final String HTTP_VERSION_1_1 = "HTTP/1.1";
  public static final String HTTP_VERSION_1_0 = "HTTP/1.0";
  
  public static final int DEFAULT_READ_TIMEOUT = 30000;
  public static final int DEFAULT_HTTP_PORT = 80;
  public static final int DEFAULT_HTTPS_PORT = 443;
  public static final Map<String, String> DEFAULT_HEADERS_MAP;
  
  static {
    HashMap<String, String> dh = new HashMap<String, String>();
    // header keys should always be lower case
    dh.put(HTTP_KEY_USER_AGENT, "litesockets");
    dh.put(HTTP_KEY_ACCEPT, "*/*");
    DEFAULT_HEADERS_MAP = Collections.unmodifiableMap(dh);

  }
  public static final HTTPHeaders DEFAULT_HEADERS = 
      new HTTPHeaders(HTTPConstants.DEFAULT_HEADERS_MAP);
  public static final HTTPRequestHeader DEFAULT_REQUEST_HEADER = 
      new HTTPRequestHeader(RequestType.GET, "/", null, HTTPConstants.HTTP_VERSION_1_1);
  public static final HTTPResponseHeader OK_RESPONSE_HEADER = 
      new HTTPResponseHeader(HTTPResponseCode.OK, HTTPConstants.HTTP_VERSION_1_1);
  public static final HTTPResponseHeader NOT_FOUND_RESPONSE_HEADER = 
      new HTTPResponseHeader(HTTPResponseCode.NotFound, HTTPConstants.HTTP_VERSION_1_1);
  public static final HTTPResponseHeader SERVER_ERROR_RESPONSE_HEADER = 
      new HTTPResponseHeader(HTTPResponseCode.InternalServerError, HTTPConstants.HTTP_VERSION_1_1);
}
