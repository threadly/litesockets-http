package org.threadly.litesockets.protocols.http.response;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;

import org.threadly.concurrent.event.ListenerHelper;
import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.buffers.ReuseableMergedByteBuffers;
import org.threadly.litesockets.buffers.SimpleMergedByteBuffers;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.litesockets.protocols.ws.WebSocketFrameParser.WebSocketFrame;


/**
 * This is a simple HTTP Response parser.  It takes in Bytes and builds up an {@link HTTPResponse} object. 
 */
public class HTTPResponseProcessor {
  public static final int MAX_RESPONSE_HEADER_SIZE = 5000;
  public static final int MAX_HEADER_SIZE = 50000;

  private final ReuseableMergedByteBuffers buffers = new ReuseableMergedByteBuffers();
  private final ListenerHelper<HTTPResponseCallback> listeners = new ListenerHelper<>(HTTPResponseCallback.class);
  private HTTPResponse response;
  private int nextChunkSize = -1;
  private int currentBodySize = 0;

  /**
   * Creates a new {@link HTTPResponseProcessor}. 
   */
  public HTTPResponseProcessor() {}

  /**
   * Adds an {@link HTTPResponseCallback} to this processor that will be called back as
   * a data stream is processed.
   * 
   * @param hrc the callback to add
   */
  public void addHTTPResponseCallback(HTTPResponseCallback hrc) {
    listeners.addListener(hrc);
  }

  /**
   * Removes an {@link HTTPResponseCallback} from this processor the removed callback
   * will no longer be called.
   * 
   * @param hrc the callback to remove.
   */
  public void removeHTTPResponseCallback(HTTPResponseCallback hrc) {
    listeners.removeListener(hrc);
  }

  /**
   * Gets all listeners that are on this processor.
   * 
   * @return a collection of {@link HTTPResponseCallback} objects currently listening for data.
   */
  public Collection<HTTPResponseCallback> getAllCallbacks() {
    return listeners.getSubscribedListeners();
  }


  /**
   * byte[] to send through the processor.
   * 
   * @param ba to send through the processor.
   */
  public void processData(byte[] ba) {
    processData(ByteBuffer.wrap(ba));
  }

  /**
   * {@link ByteBuffer} to send through the processor.
   * 
   * @param bb {@link ByteBuffer} to send through the processor.
   */
  public void processData(ByteBuffer bb) {
    processData(new SimpleMergedByteBuffers(false, bb));
  }

  /**
   * {@link MergedByteBuffers} to send through the processor.
   * 
   * @param bb {@link MergedByteBuffers} to send through the processor.
   */
  public void processData(MergedByteBuffers bb) {
    buffers.add(bb);
    if(response == null && buffers.indexOf(HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR) > -1) {
      try{
        HTTPResponseHeader hrh = new HTTPResponseHeader(buffers.getAsString(buffers.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR)));
        buffers.discard(2);
        int pos = buffers.indexOf(HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR);
        HTTPHeaders hh;
        if (pos > 0) {
          hh = new HTTPHeaders(buffers.getAsString(pos));
          buffers.discard(HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR.length());
        } else {
          hh  = new HTTPHeaders(new HashMap<String, String>());
          buffers.discard(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length());
        }
        response = new HTTPResponse(hrh, hh);
        listeners.call().headersFinished(response);
        if(!response.getHeaders().isChunked() && response.getHeaders().getContentLength() == 0) {
          if(response.getResponseCode() != HTTPResponseCode.SwitchingProtocols ) {
            reset(null);
          }
        }
      } catch(Exception e) {
        reset(e);
      }
    }

    if(response != null && buffers.remaining() > 0) {
      processBody();
    }
  }


  /**
   * Called when an http response connection is closes.  Some types of responses are only completed when the connection is closed (http1.0).
   * 
   * This will finish all the callback and then reset the processor to be able to be reused if wanted.
   * 
   */
  public void connectionClosed() {
    if(response != null) {
      if(response.getHeaders().isChunked()) {
        if (this.nextChunkSize == -1 || this.nextChunkSize == 0) {
          reset(null);
        } else {
          reset(new HTTPParsingException("Did not complete chunked encoding!"));
        }
      } else {
        if(response.getHeaders().getContentLength() > 0 && response.getHeaders().getContentLength() != this.currentBodySize) {
          reset(new HTTPParsingException("Did not get complete body!"));
        } else {
          reset(null);
        }
      }
    } else {
      reset(new HTTPParsingException("No Response Received!"));
    }
    buffers.discard(buffers.remaining());
  }


  /**
   * Resets the processor and any pending buffers left in it.
   * 
   */
  public void clearBuffer() {
    reset();
    this.buffers.discard(this.buffers.remaining());
  }
  
  /**
   * Forces a reset on the HTTPProcessor.  This will call finish on any set callbacks if a request has started.
   * 
   * NOTE: any currently unprocessed buffer will remain! see {@link #clearBuffer()}
   * 
   */
  public void reset() {
    reset(null);
  }

  /**
   * Forces a reset on the HTTPProcessor with an error, call callbacks will be called with hasError if a request has started.
   * 
   * @param t exception to pass to the callbacks.
   * 
   * NOTE: any currently unprocessed buffer will remain! see {@link #clearBuffer()}
   */
  public void reset(Throwable t) {
    if(response != null && t == null) {
      listeners.call().finished();
    }
    
    if (t != null){
      listeners.call().hasError(t);
    }
    response = null;

    currentBodySize = 0;
    nextChunkSize = -1;
  }
  
  /**
   * Lets you know if we have gotten enough data to see a full response yet.
   * 
   * @return true if the response is completed false if not.
   */
  public boolean isResponseComplete() {
    return response != null;
  }
  
  /**
   * returns the total amount of unprocessable data pending.
   * 
   * @return the size of the pending unprocessed buffers.
   */
  public int getBufferSize() {
    return buffers.remaining();
  }

  private void processBody() {
    if(response.getHeaders().isChunked()) {
      processChunks();
    } else {
      if(response.getHeaders().getContentLength() != -1 && currentBodySize < response.getHeaders().getContentLength()) {
        int pull = (int)Math.min(response.getHeaders().getContentLength(), buffers.remaining());
        sendDuplicateBBtoListeners(buffers.pullBuffer(pull));
        currentBodySize+=pull;
        if(currentBodySize >= response.getHeaders().getContentLength()) {
          reset(null);
        }
      } else if (response.getHeaders().getContentLength() == -1 || response.getResponseCode() == HTTPResponseCode.SwitchingProtocols) {
        sendDuplicateBBtoListeners(buffers.pullBuffer(buffers.remaining()));
      }
    }
  }

  /**
   * Gets the current body size for the httpResponse currently in process.
   * 
   * @return the current body size in bytes.  Will be -1 if streaming or not currently processing a response.
   */
  public int getCurrentBodySize() {
    return this.currentBodySize;
  }

  /**
   * Lets you know if a response is currently being processed or not.
   * 
   * @return true if a response is currently in process, false if not.
   */
  public boolean isProcessing() {
    if(response != null) {
      return true;
    }
    return false;
  }

  private void processChunks() {
    while(buffers.remaining() > 0) {
      if(nextChunkSize == -1) {
        int pos = buffers.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
        try {
          if(pos > 0) {
            String len = buffers.getAsString(pos);
            nextChunkSize = Integer.parseInt(len.trim(), HTTPConstants.HEX_SIZE);
            buffers.discard(2);
            if(nextChunkSize == 0) {
              buffers.discard(2);
              reset(null);
              return;
            } 
          } else {
            return;
          }
        } catch(Exception e) {
          listeners.call().hasError(new HTTPParsingException("Problem reading chunk size!", e));
          return;
        }
      } else {
        if(buffers.remaining() >= nextChunkSize+2) {
          ByteBuffer bb = buffers.pullBuffer(nextChunkSize);
          buffers.discard(2);
          sendDuplicateBBtoListeners(bb);  
          nextChunkSize = -1;
        } else {
          return;
        }
      }
    }
  }

  private void sendDuplicateBBtoListeners(ByteBuffer bb) {
    for(HTTPResponseCallback hrc: listeners.getSubscribedListeners()) {
      hrc.bodyData(bb.duplicate());
    }
  }

  /**
   * Used for processing data with {@link HTTPResponseProcessor}.
   * 
   * @author lwahlmeier
   *
   */
  public interface HTTPResponseCallback {
    
    /**
     * Called when we reach a completed {@link HTTPResponse} header.
     * 
     * @param hr the completed {@link HTTPResponse}.
     */
    public void headersFinished(HTTPResponse hr);
    
    /**
     * Called after {@link #headersFinished(HTTPResponse)} is called for any body associated with the request.
     * 
     * If the body is chunked this will be called per chunk of data and the chunk header is not included.
     * 
     * @param bb the body in a {@link ByteBuffer}
     */
    public void bodyData(ByteBuffer bb);
    /**
     * If the last headersFinished was a websocket request this will be called back on each frame we get from processed data.
     * 
     * @param wsf The {@link WebSocketFrame} that was wrapping the data.
     * @param bb the payload of the frame, it will be unmasked already if its needed.
     */
    public void websocketData(WebSocketFrame wsf, ByteBuffer bb);
    
    /**
     * This is called when the http request finishes.  This can also be called if the connection is set to closed, or reset manually.
     * 
     */
    public void finished();
    
    /**
     * This is called if there is a parsing error or the connection closes before finished, or any other error reason.
     * after this is called a new {@link #headersFinished(HTTPResponse)} can still be called.
     * 
     * @param t the error that occurred.
     */
    public void hasError(Throwable t);
  }
}
