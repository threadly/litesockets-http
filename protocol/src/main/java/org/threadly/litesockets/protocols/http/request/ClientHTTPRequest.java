package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.litesockets.protocols.http.shared.HTTPAddress;

// TODO - do we want to move this into the `client`?  I see the `HTTPRequestBuilder` references it, 
// but we could create an extending class `ClientHTTPRequestBuilder` which can build this
/**
 * This contains a full HTTPRequest, including the HTTPRequest, the HTTPAddress the body and the timeout.
 * This is immutable, though an HTTPRequestBuilder can be made from it.
 */
public class ClientHTTPRequest {
  private static final Supplier<ListenableFuture<ByteBuffer>> EMPTY_BODY_SUPPLIER = 
      () -> FutureUtils.immediateResultFuture(null);
  
  private final HTTPRequest request;
  private final HTTPAddress ha;
  private final Supplier<ListenableFuture<ByteBuffer>> bodyProvider;
  private final int timeoutMS; 
  
  protected ClientHTTPRequest(HTTPRequest request, HTTPAddress ha, int timeoutMS, 
                              Supplier<ListenableFuture<ByteBuffer>> bodyProvider) {
    this.request = request;
    this.ha = ha;
    this.bodyProvider = bodyProvider == null ? EMPTY_BODY_SUPPLIER : bodyProvider;
    this.timeoutMS = timeoutMS;
  }
  
  public HTTPRequest getHTTPRequest() {
    return request;
  }

  /**
   * Returns the {@link HTTPAddress} the request is associated with.
   * 
   * @return The {@link HTTPAddress} the request will go to
   */
  public HTTPAddress getHTTPAddress() {
    return ha;
  }
  
  /**
   * Returns if there is a body associated to this request.
   * 
   * @return {@code true} if there is a body to be consumed through {@link #getBodyProvider()}
   */
  public boolean hasBody() {
    return bodyProvider != EMPTY_BODY_SUPPLIER;
  }
  
  public ListenableFuture<ByteBuffer> nextBodySection() {
    return bodyProvider.get();
  }
  
  /**
   * Returns the timeout value that this request was constructed with.
   * 
   * @return The request timeout in milliseconds
   */
  public int getTimeoutMS() {
    return this.timeoutMS;
  }

  // TODO - is this useful?  I am not seeing any cases of it being used
  public HTTPRequestBuilder makeBuilder() {
    HTTPRequestBuilder hrb = request.makeBuilder();
    hrb.setHTTPAddress(ha, false);
        
    return hrb;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((bodyProvider == EMPTY_BODY_SUPPLIER) ? EMPTY_BODY_SUPPLIER.hashCode() : 0);
    result = prime * result + ((ha == null) ? 0 : ha.hashCode());
    result = prime * result + ((request == null) ? 0 : request.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ClientHTTPRequest other = (ClientHTTPRequest) obj;
    if (bodyProvider == EMPTY_BODY_SUPPLIER ) {
      if (other.bodyProvider != EMPTY_BODY_SUPPLIER) {
        return false;
      }
    } else if (other.bodyProvider == EMPTY_BODY_SUPPLIER) {
      return false;
    } else if (ha == null) {
      if (other.ha != null) {
        return false;
      }
    } else if (!ha.equals(other.ha)) {
      return false;
    } else if (request == null) {
      if (other.request != null) {
        return false;
      }
    } else if (!request.equals(other.request)) {
      return false;
    }
    return true;
  }
}
