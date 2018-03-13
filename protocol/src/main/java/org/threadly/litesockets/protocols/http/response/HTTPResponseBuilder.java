package org.threadly.litesockets.protocols.http.response;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;

/**
 * A builder to help create {@link HTTPResponse} objects.
 * 
 * @author lwahlmeier
 *
 */
public class HTTPResponseBuilder {
  private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private HTTPResponseHeader rHeader = HTTPConstants.OK_RESPONSE_HEADER;
  
  /**
   * Creates a new {@link HTTPResponseBuilder} object.
   * 
   */
  public HTTPResponseBuilder() {
    headers.putAll(HTTPConstants.DEFAULT_HEADERS.getHeadersMap());
    headers.put(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, "0");
  }
  
  
  /**
   * Set the {@link HTTPResponseHeader} object for this HTTPResponseBuilder.
   * 
   * @param hrh the {@link HTTPResponseHeader} object to set.
   * @return the current {@link HTTPResponseBuilder} object.
   */
  public HTTPResponseBuilder setResponseHeader(HTTPResponseHeader hrh) {
    rHeader = hrh;
    return this;
  }
  
  /**
   * Set the HTTPResponse code for this builder.
   * 
   * @param rc the response code to set.
   * @return the current {@link HTTPResponseBuilder} object.
   */
  public HTTPResponseBuilder setResponseCode(HTTPResponseCode rc) {
    rHeader = new HTTPResponseHeader(rc, rHeader.getHTTPVersion());
    return this;
  }
  
  /**
   * Set the HTTPVersion for this HTTPResponseBuilder.
   * 
   * @param version the HttpVersion to set
   * @return the current {@link HTTPResponseBuilder} object.
   */
  public HTTPResponseBuilder setHTTPVersion(String version) {
    rHeader = new HTTPResponseHeader(rHeader.getResponseCode(), version);
    return this;
  }
  
  /**
   * Set a header on the HTTPResponse.
   * 
   * @param key the key for the header.
   * @param value the value in the header.
   * @return the current {@link HTTPResponseBuilder} object.
   */
  public HTTPResponseBuilder setHeader(String key, String value) {
    if(value == null) {
      headers.remove(key);
    } else {
      headers.put(key, value);
    }
    return this;
  }
  
  /**
   * Removes a header on the HTTPResponse.
   * 
   * @param key the key for the header to remove.
   * @return the current {@link HTTPResponseBuilder} object.
   */
  public HTTPResponseBuilder removeHeader(final String key) {
    headers.remove(key);
    return this;
  }
  
  /**
   * Replaces all the {@link HTTPHeaders} for this HTTPResponseBuilder with the ones provided.
   * 
   * @param hh the {@link HTTPHeaders} object to set.
   * @return the current {@link HTTPResponseBuilder} object.
   */
  public HTTPResponseBuilder replaceHTTPHeaders(final HTTPHeaders hh) {
    this.headers.clear();
    for(Entry<String, String> head: hh.getHeadersMap().entrySet()) {
      setHeader(head.getKey(), head.getValue());
    }
    return this;
  }

  /**
   * Build an {@link HTTPResponse} object from this builder.
   * 
   * @return a new {@link HTTPResponse} based on what is set in this builder.
   */
  public HTTPResponse build() {
    return new HTTPResponse(rHeader, new HTTPHeaders(headers));
  }
}
