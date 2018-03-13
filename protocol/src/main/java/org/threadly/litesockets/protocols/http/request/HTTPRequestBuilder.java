package org.threadly.litesockets.protocols.http.request;

import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.threadly.litesockets.protocols.http.shared.HTTPAddress;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPRequestType;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;
import org.threadly.util.ArgumentVerifier;

/**
 * A builder object for {@link HTTPRequest}.  This helps construct different types of httpRequests.
 */
public class HTTPRequestBuilder {


  private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private HTTPRequestHeader request = HTTPConstants.DEFAULT_REQUEST_HEADER;
  private String host = "localhost";
  private int port = HTTPConstants.DEFAULT_HTTP_PORT;
  private boolean doSSL = false;
  private ByteBuffer bodyBytes = null;
  private int timeoutMS = HTTPRequest.DEFAULT_TIMEOUT_MS;

  /**
   * Creates a new HTTPRequestBuilder object.
   */
  public HTTPRequestBuilder() {
    headers.putAll(HTTPConstants.DEFAULT_HEADERS_MAP);
    setHeader(HTTPConstants.HTTP_KEY_HOST, host);
  }

  /**
   * Creates a new HTTPRequestBuilder object from a {@link URL}.  The Path and query will be set from it.
   * 
   * @param url the {@link URL} to use to create the {@link HTTPRequestBuilder} object with.
   */
  public HTTPRequestBuilder(final URL url) {
    headers.putAll(HTTPConstants.DEFAULT_HEADERS_MAP);
    setURL(url);
  }

  /**
   * Uses a {@link URL} to set the path and query on this HTTPRequestBuilder object.
   * 
   * @param url the {@link URL} to use to set.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setURL(final URL url) {
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
      request = new HTTPRequestHeader(request.getRequestType(), tmpPath, HTTPUtils.queryToMap(q), request.getHttpVersion());
    } else {
      request = new HTTPRequestHeader(request.getRequestType(), tmpPath, null, request.getHttpVersion());
    }
    if(url.getProtocol().equalsIgnoreCase("https")) {
      doSSL = true;
    }

    setHeader(HTTPConstants.HTTP_KEY_HOST, host);
    return this;
  }

  /**
   * Set the {@link HTTPRequestHeader} object for this HTTPRequestBuilder.
   * 
   * @param hrh the {@link HTTPRequestHeader} object to set.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setHTTPRequestHeader(final HTTPRequestHeader hrh) {
    request = hrh;
    return this;
  }

  /**
   * Sets the {@link HTTPRequestType} for this request.  This will accept non-stander strings in the for the request.
   * 
   * @param rt the RequestType to set this request too.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setRequestType(final String rt) {
    this.request = new HTTPRequestHeader(rt, request.getRequestPath(), request.getRequestQuery(), request.getHttpVersion());
    return this;
  }

  /**
   * Set the HTTPVersion for this HTTPRequestBuilder.
   * 
   * @param version the HttpVersion to set
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setHTTPVersion(final String version) {
    this.request = new HTTPRequestHeader(request.getRequestType(), request.getRequestPath(), request.getRequestQuery(), version);
    return this;
  }


  /**
   * This sets the request path for the {@link HTTPRequestBuilder}.  If a query is on this path it will replace the current query
   * in this builder. 
   * 
   * @param path the path to set.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setPath(final String path) {
    if(path.contains("?")) {
      this.request = new HTTPRequestHeader(request.getRequestType(), path, HTTPUtils.queryToMap(path), request.getHttpVersion());
    } else {
      this.request = new HTTPRequestHeader(request.getRequestType(), path, request.getRequestQuery(), request.getHttpVersion());
    }
    return this;
  }

  /**
   * Set the query on this {@link HTTPRequestBuilder}.  If there are currently any query params they will be removed before this is set.
   * 
   * @param query the query string to set.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setQueryString(final String query) {
    this.request = new HTTPRequestHeader(request.getRequestType(), request.getRequestPath(), HTTPUtils.queryToMap(query), request.getHttpVersion());
    return this;
  }

  /**
   * Adds a query key/value to this {@link HTTPRequestBuilder}.  Duplicate keys can be added.
   * 
   * @param key the query key to set.
   * @param value the query value for the set key.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder appendQuery(final String key, final String value) {
    HashMap<String, String> map = new HashMap<String, String>(request.getRequestQuery());
    map.put(key, value);
    this.request = new HTTPRequestHeader(request.getRequestType(), request.getRequestPath(), map, request.getHttpVersion());
    return this;
  }

  /**
   * Removes a Key from the query portion of the http request.
   * 
   * @param key the Key to remove
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder removeQuery(final String key) {
    HashMap<String, String> map = new HashMap<String, String>(request.getRequestQuery());
    map.remove(key);
    this.request = new HTTPRequestHeader(request.getRequestType(), request.getRequestPath(), map, request.getHttpVersion());
    return this;
  }


  /**
   * Sets the {@link HTTPAddress} for this builder.  This will add a Host header into the headers of this builder
   * when this object it built.  This is also used with the {@link #buildHTTPAddress(boolean)} method.
   * 
   * @param ha the {@link HTTPAddress} to be set.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setHTTPAddress(final HTTPAddress ha) {
    setHost(ha.getHost());
    this.port = ha.getPort();
    doSSL = ha.getdoSSL();
    return this;
  }

  /**
   * Sets the Host: header in the client.  This is also used with the {@link #buildHTTPAddress(boolean)} method.
   * Setting to null will remove this header.
   * 
   * 
   * @param host the host name or ip to set.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setHost(final String host) {
    this.host = host;
    if(host != null) {
      setHeader(HTTPConstants.HTTP_KEY_HOST, host);
    } else {
      this.removeHeader(HTTPConstants.HTTP_KEY_HOST);
    }
    return this;
  }

  public HTTPRequestBuilder setSSL(final boolean doSSL) {
    this.doSSL = doSSL;
    return this;
  }


  /**
   * This sets the port to use in the {@link #buildHTTPAddress(boolean)} method.  If not set the default port
   * for the protocol type (http or https) will be used.
   * 
   * @param port port number to set.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setPort(final int port) {
    if(port < 1 || port > Short.MAX_VALUE*2) {
      throw new IllegalArgumentException("Not a valid port number: "+port);
    }
    this.port = port;
    return this;
  }

  public HTTPRequestBuilder setBody(final ByteBuffer bb) {
    if(bb != null) {
      this.bodyBytes = bb.slice();
      this.setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(bodyBytes.remaining()));
    } else {
      this.bodyBytes = null;
      this.removeHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH);
    }
    return this;
  }

  public HTTPRequestBuilder setBody(final String str) {
    return setBody(ByteBuffer.wrap(str.getBytes()));
  }

  public HTTPRequestBuilder setBody(final String str, Charset cs) {
    return setBody(ByteBuffer.wrap(str.getBytes(cs)));
  }

  public HTTPRequestBuilder setTimeout(TimeUnit unit, int size) {
    this.timeoutMS = (int)Math.min(Math.max(unit.toMillis(size),HTTPRequest.MIN_TIMEOUT_MS), HTTPRequest.MAX_TIMEOUT_MS);
    return this;
  }

  /**
   * Creates an independent copy of this {@link HTTPRequestBuilder}.
   * 
   * @return a new {@link HTTPRequestBuilder} object with all the same values set.
   */
  public HTTPRequestBuilder duplicate() {
    HTTPRequestBuilder hrb = new HTTPRequestBuilder();
    hrb.request = request;
    for(Entry<String, String> entry: headers.entrySet()) {
      hrb.setHeader(entry.getKey(), entry.getValue());
    }
    hrb.setHost(host);
    hrb.setPort(port);
    return hrb;
  }

  /**
   * Set a header on the HTTPRequest.
   * 
   * @param key the key for the header.
   * @param value the value in the header.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setHeader(final String key, final String value) {
    headers.put(key, value);
    return this;
  }

  /**
   * Removes a header on the HTTPRequest.
   * 
   * @param key the key for the header to remove.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder removeHeader(final String key) {
    headers.remove(key);
    return this;
  }


  /**
   * Replaces all the {@link HTTPHeaders} for this HTTPRequestBuilder with the ones provided.
   * 
   * @param hh the {@link HTTPHeaders} object to set.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder replaceHTTPHeaders(final HTTPHeaders hh) {
    this.headers.clear();
    for(Entry<String, String> head: hh.getHeadersMap().entrySet()) {
      setHeader(head.getKey(), head.getValue());
    }
    return this;
  }


  /**
   * Sets the {@link HTTPRequestType} for this request.  This uses the standard http request types enum.
   * 
   * @param rt the RequestType to set this request too.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setRequestType(final HTTPRequestType rt) {
    this.request = new HTTPRequestHeader(rt, request.getRequestPath(), request.getRequestQuery(), request.getHttpVersion());
    return this;
  }

  /**
   * Builds an {@link HTTPAddress} object from the set host/port name.  
   * 
   * @return a new {@link HTTPAddress} object with host/port/ssl arguments set. 
   */
  public HTTPAddress buildHTTPAddress() {
    ArgumentVerifier.assertNotNull(this.headers.get(HTTPConstants.HTTP_KEY_HOST), "Must set Host Header!");
    return new HTTPAddress(this.headers.get(HTTPConstants.HTTP_KEY_HOST), port, doSSL);
  }

  public ClientHTTPRequest buildClientHTTPRequest() {
    return new ClientHTTPRequest(buildHTTPRequest(), buildHTTPAddress(), this.timeoutMS, this.bodyBytes);
  }

  /**
   * Builds an Immutable {@link HTTPRequest} object that can be used to send a request.
   * 
   * @return an Immutable {@link HTTPRequest} object
   */
  public HTTPRequest buildHTTPRequest() {
    return new HTTPRequest(request, new HTTPHeaders(headers));
  }
}
