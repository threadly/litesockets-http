package org.threadly.litesockets.protocol.http.structures;

import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.threadly.litesockets.protocol.http.structures.HTTPConstants.PROTOCOL;
import org.threadly.litesockets.protocol.http.structures.HTTPConstants.REQUEST_TYPE;

/**
 * This is an immutable HTTPRequest object.  This is what is sent to the server when doing an
 * HTTPRequest.  This object is created via {@link HTTPRequestBuilder}. 
 */
public class HTTPRequest {
  private final HTTPAddress httpAddress;
  private final HTTPRequestHeader request;
  private final HTTPHeaders headers;
  private final ByteBuffer body;
  private final boolean isChunked;
  private final ByteBuffer cachedBuffer;
  private int readTimeout = HTTPConstants.DEFAULT_READ_TIMEOUT;
 
  protected HTTPRequest(HTTPAddress httpAddress, int timeout, HTTPRequestHeader request, HTTPHeaders headers, byte[] lbody, boolean isChunked) {
    this.request = request;
    this.readTimeout = timeout;
    this.headers = headers;
    this.body = ByteBuffer.wrap(lbody);
    this.httpAddress = httpAddress;
    this.isChunked = isChunked;
    byte[] rba = request.toString().getBytes();
    byte[] hba = headers.toString().getBytes();
    //TODO: figure out a way to save memory here, but still keep all needed objects and data....
    ByteBuffer bb = ByteBuffer.allocate(rba.length+hba.length+lbody.length+
        HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length()+HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR.length());
    bb.put(rba);
    bb.put(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.getBytes());
    bb.put(hba);
    bb.put(HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR.getBytes());
    bb.put(lbody);
    bb.flip();
    cachedBuffer = bb.asReadOnlyBuffer();
  }
  
  public String getHost() {
    return httpAddress.getHost();
  }
  
  public boolean isChunked() {
    return isChunked;
  }
  
  public int getPort() {
    return httpAddress.getPort();
  }
  
  public boolean doSSL() {
    return httpAddress.getdoSSL();
  }
  
  public HTTPAddress getHTTPAddress() {
    return httpAddress;
  }
  
  public ByteBuffer getBody() {
    return body.asReadOnlyBuffer();
  }
  
  public HTTPHeaders getHTTPHeaders() {
    return headers;
  }
  
  public HTTPRequestHeader getHTTPRequestHeaders() {
    return request;
  }
  
  public int getTimeout() {
    return readTimeout;
  }
  
  @Override
  public String toString() {
    return this.request.toString()+HTTPConstants.HTTP_NEWLINE_DELIMINATOR+
        this.headers.toString()+HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR+
        "bodysize:"+body.remaining();
  }
  
  public ByteBuffer getRequestBuffer() {
    return cachedBuffer.duplicate();
  }
  
  public HTTPRequestBuilder makeBuilder() {
    HTTPRequestBuilder hrb = new HTTPRequestBuilder().setHTTPHeaders(headers).setHTTPRequestHeader(request);
    hrb.setHTTPAddress(httpAddress);
    hrb.setBody(body.array());
    hrb.setReadTimeout(this.readTimeout);
    if(this.isChunked) {
      hrb.enableChunked();
    }
    return hrb;
  }
  
  /**
   * A builder object for HTTPRequests.  This helps construct different types of httpRequests. 
   *
   */
  public static class HTTPRequestBuilder {
    private HTTPRequestHeader request = HTTPUtils.DEFAULT_REQUEST_HEADER;
    private Map<String, String> headers = new HashMap<String, String>(HTTPConstants.DEFAULT_HEADERS_MAP);
    private boolean doSSL = false;
    private String host = "localhost";
    private int port = 1;
    private int readTimeout = HTTPConstants.DEFAULT_READ_TIMEOUT; 
    private boolean chunked = false;
    private byte[] body = new byte[0];
    
    public HTTPRequestBuilder(){
      setHeader(HTTPConstants.HTTP_KEY_HOST, host);
    }
    
    public HTTPRequestBuilder(URL url) {
      setURL(url);
    }
    
    public HTTPRequestBuilder setURL(URL url) {
      if(url.getProtocol().equalsIgnoreCase(PROTOCOL.HTTP.toString())){
        doSSL = false;
      } else if(url.getProtocol().equalsIgnoreCase(PROTOCOL.HTTPS.toString())) {
        doSSL = true;
      } else {
        throw new IllegalArgumentException(url.getProtocol() + " is not a valid HTTP protocol");
      }
      
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
        request = new HTTPRequestHeader(request.getRequestType(), tmpPath, HTTPUtils.parseQueryString(q), request.getHttpVersion());
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
      for(Entry<String, String> head: hh.headers.entrySet()) {
        setHeader(head.getKey(), head.getValue());
      }
      return this;
    }
    
    public HTTPRequestBuilder setHTTPAddress(HTTPAddress ha) {
      this.host = ha.getHost();
      this.port = ha.getPort();
      this.doSSL = ha.getdoSSL();
      return this;
    }
    
    public HTTPRequestBuilder duplicate() {
      HTTPRequestBuilder hrb = new HTTPRequestBuilder();
      hrb.setReadTimeout(readTimeout).setHost(host).setPort(port);
      hrb.request = request;
      hrb.chunked = chunked;
      hrb.doSSL = doSSL;
      hrb.body = new byte[body.length];
      for(Entry<String, String> entry: headers.entrySet()) {
        hrb.setHeader(entry.getKey(), entry.getValue());
      }
      System.arraycopy(body, 0, hrb.body, 0, hrb.body.length);
      return hrb;
    }
    
    public HTTPRequestBuilder enableSSL() {
      doSSL = true;
      return this;
    }
    
    public HTTPRequestBuilder disableSSL() {
      doSSL = false;
      return this;
    }
    
    public HTTPRequestBuilder setReadTimeout(int timeout) {
      this.readTimeout = timeout;
      return this;
    }
    
    public HTTPRequestBuilder setHost(String host) {
      this.host = host;
      setHeader(HTTPConstants.HTTP_KEY_HOST, host);
      return this;
    }
    
    public HTTPRequestBuilder enableChunked() {
      setHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING, "chunked");
      removeHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH);
      chunked = true;
      setBody(body);
      return this;
    }
    
    public HTTPRequestBuilder disableChunked() {
      removeHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING);
      chunked = false;
      removeBody();
      return this;
    }
    
    public boolean isChunkedRequest() {
      return chunked;
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
      this.request = new HTTPRequestHeader(request.getRequestType(), request.getRequestPath(), HTTPUtils.parseQueryString(query), request.getHttpVersion());
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
    
    public HTTPRequestBuilder removeBody() {
      if(body.length != 0) {
        body = new byte[0];
      }
      return this;
    }
    
    public HTTPRequestBuilder setBody(byte[] body) {
      return removeBody().appendBody(body);
    }
    
    public HTTPRequestBuilder setBody(String body) {
      return removeBody().setBody(body.getBytes());
    }
    
    /**
     * This is mainly used when Chunked Encoding is enabled.  Every *appendBody* will add a new chunk
     * as some http protocol depend on it for there protocol.
     * 
     * @param appendBody the bytearray to append to the current body.
     * @return builder object.
     */
    public HTTPRequestBuilder appendBody(byte[] appendBody) {
      if(chunked) {
        appendBody = HTTPUtils.wrapInChunk(appendBody);
      }
      if(body.length != 0) {
        byte[] tmpBA = new byte[appendBody.length+body.length];
        System.arraycopy(body, 0, tmpBA, 0, body.length);
        System.arraycopy(appendBody, 0, tmpBA, body.length, appendBody.length);
        this.body = tmpBA;
      } else {
        this.body = appendBody;
      }
      if(!chunked) {
        this.setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(body.length));
      }
      return this;
    }
    
    public HTTPRequestBuilder setRequestType(REQUEST_TYPE rt) {
      this.request = new HTTPRequestHeader(rt, request.getRequestPath(), request.getRequestQuery(), request.getHttpVersion());
      return this;
    }
    
    public HTTPRequest build() {
      return doBuild(true);
    }
    
    public HTTPRequest buildHeadersOnly() {
      return doBuild(false);
    }
    
    private HTTPRequest doBuild(boolean addBody) {
      byte[] localBody;
      if(addBody) {
        localBody = body;
      } else {
        localBody = new byte[0];
      }
      
      if(doSSL) {
        HTTPAddress had = new HTTPAddress(host, port, true);
        return new HTTPRequest(had, this.readTimeout, request, new HTTPHeaders(headers), localBody, chunked);
      } else {
        HTTPAddress had = new HTTPAddress(host, port, false);
        return new HTTPRequest(had, this.readTimeout, request, new HTTPHeaders(headers), localBody, chunked);
      }
    }
  }
}
