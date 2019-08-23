package org.threadly.litesockets.protocols.http.response;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;

import org.threadly.litesockets.buffers.SimpleMergedByteBuffers;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;

/**
 *  An Immutable HTTPResponse object.  This contains all information from an HTTP Response.
 */
public class HTTPResponse {
  private final HTTPResponseHeader rHeader;
  private final HTTPHeaders headers;
  
  /**
   * Creates an {@link HTTPResponse} object.
   * 
   * @param rHeader the {@link HTTPResponseHeader} to use for this {@link HTTPResponse}.
   * @param headers the {@link HTTPHeaders} to use for this {@link HTTPResponse}.
   */
  public HTTPResponse(HTTPResponseHeader rHeader, HTTPHeaders headers) {
    this.rHeader = rHeader;
    this.headers = headers;
  }

  /**
   * Creates an {@link HTTPResponse} object.
   * 
   * @param rCode the {@link HTTPResponseCode} to use for this {@link HTTPResponse}.
   * @param httpVersion the HTTP version to use for this {@link HTTPResponse}.
   * @param headers the {@link HTTPHeaders} to use for this {@link HTTPResponse}.
   */
  public HTTPResponse(HTTPResponseCode rCode, String httpVersion, Map<String, String> headers) {
    rHeader = new HTTPResponseHeader(rCode, httpVersion);
    this.headers = new HTTPHeaders(headers);
  }
  
  /**
   * Gets the {@link HTTPResponseHeader} for this {@link HTTPResponse} object.
   * 
   * @return the {@link HTTPResponseHeader} for this {@link HTTPResponse} object.
   */
  public HTTPResponseHeader getResponseHeader() {
    return rHeader;
  }
  
  
  /**
   * Gets the {@link HTTPHeaders} for this {@link HTTPResponse} object.
   * 
   * @return the {@link HTTPHeaders} for this {@link HTTPResponse} object.
   */
  public HTTPHeaders getHeaders() {
    return headers;
  }
  
  /**
   * Gets the {@link HTTPResponseCode} for this {@link HTTPResponse} object.
   * 
   * @return the {@link HTTPResponseCode} for this {@link HTTPResponse} object.
   */
  public HTTPResponseCode getResponseCode() {
    return rHeader.getResponseCode();
  }
  
  /**
   * Gets this {@link HTTPResponse} as a {@link ByteBuffer}.
   * 
   * @deprecated Please use {@link #getMergedByteBuffers()} to avoid copying the data
   * 
   * @return a {@link ByteBuffer} of this {@link HTTPResponse}.
   */
  @Deprecated
  public ByteBuffer getByteBuffer() {
    ByteBuffer combined = ByteBuffer.allocate(headers.toString().length() + rHeader.length() + 
        HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length() + 
        HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length());
    combined.put(rHeader.getByteBuffer());
    combined.put(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.getBytes());
    combined.put(headers.toString().getBytes());
    combined.put(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.getBytes());
    combined.flip();
    return combined;
  }
  
  /**
   * Gets this {@link HTTPResponse} as a {@link ByteBuffer}.
   * 
   * @return a {@link ByteBuffer} of this {@link HTTPResponse}.
   */
  public SimpleMergedByteBuffers getMergedByteBuffers() {
    return new SimpleMergedByteBuffers(true, 
                                       rHeader.getByteBuffer(), 
                                       HTTPConstants.HTTP_NEWLINE_DELIMINATOR_BUFFER.duplicate(), 
                                       ByteBuffer.wrap(headers.toString().getBytes()), 
                                       HTTPConstants.HTTP_NEWLINE_DELIMINATOR_BUFFER.duplicate());
  }
  
  @Override
  public int hashCode() {
    return rHeader.hashCode() ^ headers.hashCode();
  }
  
  @Override
  public boolean equals(Object o) {
    if(o == this) {
      return true;
    } else if(o instanceof HTTPResponse) {
      HTTPResponse hr = (HTTPResponse)o;
      if(hr.rHeader.equals(rHeader) && hr.headers.equals(headers)) {
        return true;
      }
    }
    return false;
  }
  
  @Override
  public String toString() {
    return this.rHeader+HTTPConstants.HTTP_NEWLINE_DELIMINATOR+headers+HTTPConstants.HTTP_NEWLINE_DELIMINATOR;
  }
  
  /**
   * Creates an {@link HTTPResponseBuilder} from this {@link HTTPResponse}.  This allows for easy modification of the Response.
   * 
   * @return an {@link HTTPResponseBuilder} from this {@link HTTPResponse}. 
   */
  public HTTPResponseBuilder makeBuilder() {
    return new HTTPResponseBuilder().setResponseHeader(rHeader).replaceHTTPHeaders(headers);
  }
  
  /**
   * Creates an {@link HTTPResponseBuilder} from this {@link HTTPResponse}.  This allows for easy modification of the Response.
   * 
   * @return an {@link HTTPResponseBuilder} from this {@link HTTPResponse}. 
   */
  public static HTTPResponseBuilder builder() {
    return new HTTPResponseBuilder();
  }
  
  /**
   * Creates a new {@link HTTPResponse} object from a string.
   * 
   * @param response the HTTP Response string to parse.
   * @return an {@link HTTPResponse} from the provided string.
   * @throws HTTPParsingException is thrown if there are any problems parsing the HTTPResponse.
   */
  public static HTTPResponse parseResponse(final String response) throws HTTPParsingException {
    try{
      int reqend = response.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
      HTTPResponseHeader hrh = new HTTPResponseHeader(response.substring(reqend));
      int pos = response.indexOf(HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR);
      HTTPHeaders hh;
      if (pos > 0) {
        hh = new HTTPHeaders(response.substring(reqend+HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length(), pos));
      } else {
        hh  = new HTTPHeaders(Collections.emptyMap());
      }
      return new HTTPResponse(hrh, hh);
    } catch(Exception e) {
      throw new HTTPParsingException(e);
    }
  }

}
