package org.threadly.litesockets.protocols.http.request;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.threadly.concurrent.SubmitterExecutor;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.buffers.ReuseableMergedByteBuffers;
import org.threadly.litesockets.protocols.http.request.ClientHTTPRequest.BodyConsumer;
import org.threadly.litesockets.protocols.http.shared.HTTPAddress;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;
import org.threadly.litesockets.protocols.http.shared.HTTPRequestMethod;
import org.threadly.litesockets.protocols.http.shared.HTTPUtils;
import org.threadly.util.ArgumentVerifier;
import org.threadly.util.ArrayIterator;

// TODO - I think the fact that this builds multiple types of Requests can be confusing
//        We should evaluate this API and the similar todo comment in ClientHTTPRequest
/**
 * A builder object for {@link HTTPRequest}.  This helps construct different types of httpRequests.
 */
public class HTTPRequestBuilder {
  public static final int MAX_HTTP_BUFFERED_RESPONSE = 1048576;  //1MB
  
  private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private HTTPRequestHeader request = HTTPConstants.DEFAULT_REQUEST_HEADER;
  private String host = "localhost";
  private int port = HTTPConstants.DEFAULT_HTTP_PORT;
  private boolean doSSL = false;
  private Supplier<ListenableFuture<ByteBuffer>> bodySupplier = null;
  private BodyConsumer bodyConsumer = null;
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
      request = new HTTPRequestHeader(request.getRequestMethod(), tmpPath, HTTPUtils.queryToMap(q), request.getHttpVersion());
    } else {
      request = new HTTPRequestHeader(request.getRequestMethod(), tmpPath, null, request.getHttpVersion());
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
   * Sets the {@link HTTPRequestMethod} for this request.  This will accept non-stander strings in the for the request.
   * 
   * @param rm the http request method to set this request too.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setRequestMethod(final String rm) {
    this.request = new HTTPRequestHeader(rm, request.getRequestPath(), request.getRequestQuery(), 
                                         request.getHttpVersion());
    return this;
  }

  /**
   * Set the HTTPVersion for this HTTPRequestBuilder.
   * 
   * @param version the HttpVersion to set
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setHTTPVersion(final String version) {
    this.request = new HTTPRequestHeader(request.getRequestMethod(), request.getRequestPath(), 
                                         request.getRequestQuery(), version);
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
      this.request = new HTTPRequestHeader(request.getRequestMethod(), path, 
                                           HTTPUtils.queryToMap(path), request.getHttpVersion());
    } else {
      this.request = new HTTPRequestHeader(request.getRequestMethod(), path, 
                                           request.getRequestQuery(), request.getHttpVersion());
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
    this.request = new HTTPRequestHeader(request.getRequestMethod(), request.getRequestPath(), HTTPUtils.queryToMap(query), request.getHttpVersion());
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
    HashMap<String, List<String>> map = new HashMap<>(request.getRequestQuery());
    map.computeIfAbsent(key, (ignored) -> new ArrayList<>(1)).add(value);
    this.request = new HTTPRequestHeader(request.getRequestMethod(), request.getRequestPath(), map, request.getHttpVersion());
    return this;
  }

  /**
   * Removes a Key from the query portion of the http request.
   * 
   * @param key the Key to remove
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder removeQuery(final String key) {
    HashMap<String, List<String>> map = new HashMap<>(request.getRequestQuery());
    map.remove(key);
    this.request = new HTTPRequestHeader(request.getRequestMethod(), request.getRequestPath(), map, request.getHttpVersion());
    return this;
  }

  /**
   * Sets the {@link HTTPAddress} for this builder.  This will add a Host header into the headers of this builder
   * when this object it built.  This is also used with the {@link #buildHTTPAddress()} method.
   * 
   * @param ha the {@link HTTPAddress} to be set.
   * @param setHostHeader true if you want to chage the Host header to the host in the HTTPAddress, false if you do not.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setHTTPAddress(final HTTPAddress ha, boolean setHostHeader) {
    setHost(ha.getHost(), setHostHeader);
    this.port = ha.getPort();
    doSSL = ha.getdoSSL();
    return this;
  }
  
  /**
   * Sets the Host: header in the client.  This is also used with the {@link #buildHTTPAddress()} method.
   * Setting to null will remove this header.
   * 
   * NOTE: this will override the HTTP Host header.
   * 
   * 
   * @param host the host name or ip to set.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setHost(final String host) {
    return setHost(host, true);
  }

  /**
   * Sets the Host: header in the client.  This is also used with the {@link #buildHTTPAddress()} method.
   * Setting to null will remove this header.
   * 
   * 
   * @param host the host name or ip to set.
   * @param setHeader lets you choose if you want to set the host header as well.  Set to false if you want to have a different 
   * HTTPAddress host then whats in the http host header.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setHost(final String host, boolean setHeader) {
    this.host = host;
    if(host != null) {
      if(setHeader) {
        setHeader(HTTPConstants.HTTP_KEY_HOST, host);
      }
    } else {
      this.removeHeader(HTTPConstants.HTTP_KEY_HOST);
    }
    return this;
  }

  /**
   * Sets if the request should be made using ssl or not.
   * 
   * @param doSSL {@code true} if ssl should be used.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setSSL(final boolean doSSL) {
    this.doSSL = doSSL;
    return this;
  }

  /**
   * This sets the port to use in the {@link #buildHTTPAddress()} method.  If not set the default port
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

  /**
   * Set a single part body to send in the request.
   * 
   * @param bb The buffer to be provided or {@code null} to unset the body
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setBody(final ByteBuffer bb) {
    if(bb != null && bb.hasRemaining()) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Iterator<ListenableFuture<ByteBuffer>> it = 
          ArrayIterator.makeIterator(new ListenableFuture[] { 
            FutureUtils.immediateResultFuture(bb.slice()), FutureUtils.immediateResultFuture(null) } );
      this.bodySupplier = it::next;
      this.setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(bb.remaining()));
    } else {
      this.bodySupplier = null;
      this.removeHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH);
    }
    removeHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING);
    return this;
  }

  /**
   * Set a single part body to send in the request.
   * 
   * @param str The body contents represented as a string
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setBody(final String str) {
    return setBody(ByteBuffer.wrap(str.getBytes()));
  }

  /**
   * Set a single part body to send in the request.
   * 
   * @param str The body contents represented as a string
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setBody(final String str, Charset cs) {
    return setBody(ByteBuffer.wrap(str.getBytes(cs)));
  }

  /**
   * Set a body to be consumed from an {@link InputStream}.
   * 
   * @param executor Executor to do blocking read from InputStream on
   * @param bodySize The total size to be consumed from the InputStream
   * @param bodyStream The stream to consume from
   * @param bufferSize The size per-read from the stream, up to twice of this may be allocated at a time
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setStreamedBody(final SubmitterExecutor executor, final int bodySize, 
                                            final InputStream bodyStream, 
                                            final int bufferSize) {
    return setStreamedBody(bodySize, bodyProducer(executor, bodyStream, bufferSize));
  }

  /**
   * Set a body from a supplier of {@link ListenableFuture}'s.  Each future should provide the next 
   * part of the body.  Once a future returns a {@code null} or otherwise empty {@link ByteBuffer}, 
   * it is assumed the body is complete and will not be invoked for more content.
   * <p>
   * The Supplier will NOT be invoked concurrently, however the returned buffer of the last invoke 
   * CAN'T be reused.  The next write buffer will be requested before the last one has finished 
   * sending in order to facilitate smooth performance when reading content to send has a delay.  
   * There will never be more than 2 unsent writes requested, so buffer reuse can happen for every 
   * other request.
   * 
   * @param bodySize The total size to be consumed from the InputStream
   * @param bodySupplier The supplier of writes, till {@code null} ends the body stream
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setStreamedBody(final int bodySize, 
                                            final Supplier<ListenableFuture<ByteBuffer>> bodySupplier) {
    this.bodySupplier = bodySupplier;
    this.removeHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING);
    this.setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(bodySize));
    return this;
  }

  /**
   * Set a chunked body to be consumed from an {@link InputStream}.  Each read will be turned into 
   * an HTTP chunk.
   * 
   * @param executor Executor to do blocking read from InputStream on
   * @param bodyStream The stream to consume from
   * @param bufferSize The size per-read from the stream, up to twice of this may be allocated at a time
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setChunkedBody(final SubmitterExecutor executor, 
                                           final InputStream bodyStream, 
                                           final int bufferSize) {
    return setChunkedBody(bodyProducer(executor, bodyStream, bufferSize));
  }

  /**
   * Set a chunked body from a supplier of {@link ListenableFuture}'s.  Each future should provide 
   * the next chunk for the body.  Once a future returns a {@code null} or otherwise empty 
   * {@link ByteBuffer}, it is assumed the body is complete and will not be invoked for more 
   * content.
   * <p>
   * The Supplier will NOT be invoked concurrently, however the returned buffer of the last invoke 
   * CAN'T be reused.  The next write buffer will be requested before the last one has finished 
   * sending in order to facilitate smooth performance when reading content to send has a delay.  
   * There will never be more than 2 unsent writes requested, so buffer reuse can happen for every 
   * other request.
   * 
   * @param bodySupplier The supplier of writes, till {@code null} ends the body stream
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setChunkedBody(final Supplier<ListenableFuture<ByteBuffer>> bodySupplier) {
    this.bodySupplier = bodySupplier;
    this.removeHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH);
    this.setHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING, "chunked");
    return this;
  }

  private Supplier<ListenableFuture<ByteBuffer>> bodyProducer(final SubmitterExecutor executor, 
                                                              final InputStream bodyStream, 
                                                              final int bufferSize) {
    Callable<ByteBuffer> streamReader = new Callable<ByteBuffer>() {
      private boolean use0 = true;
      private ByteBuffer buffer0 = ByteBuffer.allocate(bufferSize);
      private ByteBuffer buffer1 = null;  // lazily set
      
      @Override
      public ByteBuffer call() throws Exception {
        if (use0) {
          use0 = false;
          
          return read(buffer0);
        } else {
          use0 = true;
          
          if (buffer1 == null) {
            buffer1 = ByteBuffer.allocate(bufferSize);
          }
          
          return read(buffer1);
        }
      }
      
      private ByteBuffer read(ByteBuffer buffer) throws IOException {
        int c = bodyStream.read(buffer.array());
        if (c > 0) {
          buffer.position(0);
          buffer.limit(c);
          return buffer;
        } else {
          return null;
        }
      }
    };
    return () -> executor.submit(streamReader);
  }
  
  /**
   * This will set {@link #setBodyConsumer(BodyConsumer)} with a buffered consumer at a maximum 
   * size provided.  If not set the default {@link #MAX_HTTP_BUFFERED_RESPONSE} will be used.
   * 
   * @param size Maximum response size to allow / buffer
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setMaximumBufferedResponseSize(int size) {
    return setBodyConsumer(new BufferedBodyConsumer(size));
  }
  
  /**
   * Set the {@link BodyConsumer} to accept the response body for this request.  If not set a 
   * {@link BufferedBodyConsumer} will be used with a maximum size of 
   * {@link #MAX_HTTP_BUFFERED_RESPONSE}.
   * 
   * @param bodyConsumer Consumer to accept body content
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setBodyConsumer(BodyConsumer bodyConsumer) {
    this.bodyConsumer = bodyConsumer;
    return this;
  }

  public HTTPRequestBuilder setTimeout(long value, TimeUnit unit) {
    if (value <= 0) {
      this.timeoutMS = -1;
    } else {
      this.timeoutMS = (int)Math.max(unit.toMillis(value), HTTPRequest.MIN_TIMEOUT_MS);
    }
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
    hrb.setSSL(doSSL);
    hrb.setTimeout(timeoutMS, TimeUnit.MILLISECONDS);
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
   * Sets the {@link HTTPRequestMethod} for this request.  This uses the standard http request methods enum.
   * 
   * @param rm the http request method to set this request too.
   * @return the current {@link HTTPRequestBuilder} object.
   */
  public HTTPRequestBuilder setRequestMethod(final HTTPRequestMethod rm) {
    this.request = new HTTPRequestHeader(rm, request.getRequestPath(), request.getRequestQuery(), 
                                         request.getHttpVersion());
    return this;
  }

  /**
   * Builds an {@link HTTPAddress} object from the set host/port name.  
   * 
   * @return a new {@link HTTPAddress} object with host/port/ssl arguments set. 
   */
  public HTTPAddress buildHTTPAddress() {
    String lhost = host;
    if(lhost == null) {
      lhost = headers.get(HTTPConstants.HTTP_KEY_HOST);
    }
    ArgumentVerifier.assertNotNull(lhost, "Host must be set to create HTTPAddress!!!");
    return new HTTPAddress(this.headers.get(HTTPConstants.HTTP_KEY_HOST), port, doSSL);
  }

  public ClientHTTPRequest buildClientHTTPRequest() {
    BodyConsumer bodyConsumer = this.bodyConsumer;
    if (bodyConsumer == null) {
      bodyConsumer = new BufferedBodyConsumer(MAX_HTTP_BUFFERED_RESPONSE);
    }
    return new ClientHTTPRequest(buildHTTPRequest(), buildHTTPAddress(), this.timeoutMS, 
                                 this.bodySupplier, bodyConsumer);
  }

  /**
   * Builds an Immutable {@link HTTPRequest} object that can be used to send a request.
   * 
   * @return an Immutable {@link HTTPRequest} object
   */
  public HTTPRequest buildHTTPRequest() {
    return new HTTPRequest(request, new HTTPHeaders(headers));
  }
  
  /**
   * Default {@link BodyConsumer} which will buffer the response in heap.  Once completed the entire 
   * body will be returned in the final response object.
   */
  public static class BufferedBodyConsumer implements BodyConsumer {
    private final int maxResponseSize;
    private ReuseableMergedByteBuffers responseMBB = new ReuseableMergedByteBuffers();
    
    /**
     * Construct a new {@link BufferedBodyConsumer} with the specified maximum response size.
     * 
     * @param maxResponseSize Maximum size to allow response to buffer
     */
    public BufferedBodyConsumer(int maxResponseSize) {
      this.maxResponseSize = maxResponseSize;
    }

    @Override
    public void accept(ByteBuffer bb) throws HTTPParsingException {
      if(responseMBB.remaining() + bb.remaining() > maxResponseSize) {
        throw new HTTPParsingException("Response Body to large!");
      }
      responseMBB.add(bb);
    }

    @Override
    public MergedByteBuffers finishBody() {
      return responseMBB.duplicateAndClean();
    }
  }
}
