package org.threadly.litesockets.protocols.http.response;

import java.nio.ByteBuffer;

import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;

/**
 * An Immutable object of the HTTP Response header.  Basically the first line in the Header of an HTTP response. 
 */
public class HTTPResponseHeader {
  private static final int MAX_RESPONSE_ITEMS = 3;
  private final String rawResponse;
  private final HTTPResponseCode hrc;
  private final String httpVersion;

  /**
   * This parses an http response string and creates an Immutable {@link HTTPResponse} object for it.
   * 
   * @param responseHeader the string to parse into a {@link HTTPResponse} .
   * @throws IllegalArgumentException If the header fails to parse.
   */
  public HTTPResponseHeader(final String responseHeader) {
    this.rawResponse = responseHeader.trim();
    String[] tmp = rawResponse.split(" ", MAX_RESPONSE_ITEMS);
    try {
      httpVersion = tmp[0].trim();
      if(!httpVersion.equalsIgnoreCase(HTTPConstants.HTTP_VERSION_1_1) && !httpVersion.equalsIgnoreCase(HTTPConstants.HTTP_VERSION_1_0)) {
        throw new IllegalArgumentException("Unknown HTTP Version!:"+httpVersion);
      }
      hrc = HTTPResponseCode.findResponseCode(Integer.parseInt(tmp[1].trim()));
    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
      throw new IllegalArgumentException("Invalid Response Header! :"+responseHeader);
    }
  }

  /**
   * This parses an http response string and creates an Immutable {@link HTTPResponse} object for it.
   * 
   * @param rCode the string to parse into a {@link HTTPResponse}.
   * @param httpVersion the httpVersion to set.
   * @throws IllegalArgumentException If the header fails to parse.
   */
  public HTTPResponseHeader(final HTTPResponseCode rCode, final String httpVersion) {
    if(!httpVersion.equals(HTTPConstants.HTTP_VERSION_1_1) && !httpVersion.equals(HTTPConstants.HTTP_VERSION_1_0)) {
      throw new IllegalArgumentException("Unknown HTTP Version!:"+httpVersion);
    }
    hrc = rCode;
    this.httpVersion = httpVersion;
    rawResponse = (this.httpVersion+" "+hrc.getId()+" "+hrc.toString());
  }
  
  /**
   * Gets the HTTPResponseCode set in this response.
   * 
   * @return the HTTPResponseCode type.
   */
  public HTTPResponseCode getResponseCode() {
    return hrc;
  }

  /**
   * Gets the http version.
   * 
   * @return the http version.
   */
  public String getHTTPVersion() {
    return httpVersion;
  }

  /**
   * The length in bytes of the http response header.
   * 
   * @return length in bytes of the http response header.
   */
  public int length() {
    return rawResponse.length();
  }

  /**
   * Returns the header as a read-only {@link ByteBuffer}.
   * 
   * The newline/carriage return is not included!
   * 
   * @return a {@link ByteBuffer} of the response header.
   */
  public ByteBuffer getByteBuffer() {
    return ByteBuffer.wrap(this.rawResponse.getBytes()).asReadOnlyBuffer();
  }

  @Override
  public int hashCode() {
    return rawResponse.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if(o == this) {
      return true;
    } else if (o instanceof HTTPResponseHeader) {
      return ((HTTPResponseHeader)o).hrc.equals(hrc) && ((HTTPResponseHeader)o).httpVersion.equals(httpVersion);
    }
    return false;
  }

  @Override
  public String toString() {
    return rawResponse;
  }
}
