package org.threadly.litesockets.protocols.http.response;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;

/**
 * A builder to help create {@link HTTPResponse} objects.
 * 
 * @author lwahlmeier
 *
 */
public class HTTPResponseBuilder {
  private final Map<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
  private HTTPResponseHeader rHeader = HTTPConstants.OK_RESPONSE_HEADER;
  
  public HTTPResponseBuilder() {}
  
  
  public HTTPResponseBuilder setHeader(String key, String value) {
    headers.put(key, value);
    return this;
  }
  
  public HTTPResponseBuilder setHeaders(HTTPHeaders hh) {
    this.headers.clear();
    for(Entry<String, String> head: hh.getHeadersMap().entrySet()) {
      setHeader(head.getKey(), head.getValue());
    }
    return this;
  }
  
  public HTTPResponseBuilder setResponseHeader(HTTPResponseHeader hrh) {
    rHeader = hrh;
    return this;
  }
  
  public HTTPResponse build() {
    return new HTTPResponse(rHeader, new HTTPHeaders(headers));
  }
}
