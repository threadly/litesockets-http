package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;

/**
 * This is an immutable HTTPRequest object.  This is what is sent to the server when doing an
 * HTTPRequest.  This object is created via {@link HTTPRequestBuilder}. 
 */
public class HTTPRequest {
  private final HTTPRequestHeader request;
  private final HTTPHeaders headers;
  private transient volatile ByteBuffer cachedBuffer;
  
  protected HTTPRequest(HTTPRequestHeader request, HTTPHeaders headers) {
    this.request = request;
    this.headers = headers;
  }
  
  /**
   * Returns the {@link HTTPHeaders} object for this request.
   * 
   * @return the {@link HTTPHeaders} object for this request.
   */
  public HTTPHeaders getHTTPHeaders() {
    return headers;
  }
  
  /**
   * Returns the {@link HTTPRequestHeader} object for this request.
   * 
   * @deprecated Please use {@link #getHTTPRequestHeader()} instead.
   * 
   * @return the {@link HTTPRequestHeader} object for this request.
   */
  @Deprecated
  public HTTPRequestHeader getHTTPRequestHeaders() {
    return request;
  }
  
  /**
   * Returns the {@link HTTPRequestHeader} object for this request.
   * 
   * @return the {@link HTTPRequestHeader} object for this request.
   */
  public HTTPRequestHeader getHTTPRequestHeader() {
    return request;
  }
  
  /**
   * Returns a {@link ByteBuffer} for this header.  The buffer is read-only.
   * 
   * @return a {@link ByteBuffer} for this header
   */
  public ByteBuffer getByteBuffer() {
    if(cachedBuffer == null) {
      ByteBuffer combined = ByteBuffer.allocate(headers.toString().length() + request.length() + 
          HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length() + 
          HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length());

      combined.put(request.getByteBuffer());
      combined.put(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.getBytes());
      combined.put(headers.toString().getBytes());
      combined.put(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.getBytes());
      combined.flip();
      cachedBuffer = combined.asReadOnlyBuffer();
    }
    return cachedBuffer.duplicate();
  }
  
  @Override
  public String toString() {
    return request.toString()+
        HTTPConstants.HTTP_NEWLINE_DELIMINATOR+
        headers.toString()+
        HTTPConstants.HTTP_NEWLINE_DELIMINATOR;
  }
  
  @Override
  public int hashCode() {
    return request.hashCode() ^ headers.hashCode();
  }
  
  @Override
  public boolean equals(Object o) {
    if(o == this) {
      return true;
    } else if(o instanceof HTTPRequest) {
      HTTPRequest hr = (HTTPRequest)o;
      if(hr.request.equals(request) && hr.headers.equals(headers)) {
        return true;
      }
    }
    return false;
  }
  
  /**
   * Create an {@link HTTPRequestBuilder} from this header.
   * 
   * @return an {@link HTTPRequestBuilder} based on this HTTPRequest.
   */
  public HTTPRequestBuilder makeBuilder() {
    HTTPRequestBuilder hrb = new HTTPRequestBuilder().replaceHTTPHeaders(headers).setHTTPRequestHeader(request);
    return hrb;
  }
  
  
  /**
   * Creates a new {@link HTTPRequestBuilder}.
   * 
   * @return a new {@link HTTPRequestBuilder}.
   */
  public static HTTPRequestBuilder builder() {
    return new HTTPRequestBuilder();
  }
}
