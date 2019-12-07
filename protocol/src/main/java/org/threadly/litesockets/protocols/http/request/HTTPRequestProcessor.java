package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;
import java.text.ParseException;

import org.threadly.concurrent.event.ListenerHelper;
import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.buffers.ReuseableMergedByteBuffers;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;
import org.threadly.litesockets.protocols.websocket.WSFrame;
import org.threadly.litesockets.protocols.websocket.WSUtils;
import org.threadly.util.ArgumentVerifier;

/**
 * This processes byte data and turns it into HTTPrequests.  It does this through callbacks to a {@link HTTPRequestCallback} interface.  
 * These callbacks happen on the same thread that called to process the data.
 * 
 * @author lwahlmeier
 *
 */
public class HTTPRequestProcessor {
  public static final int MAX_HEADER_LENGTH = 1024*128;
  public static final int MAX_HEADER_ROW_LENGTH = 1024*8;
  private static final int MAX_CHUNK_SIZE = 1024*20;

  private final ReuseableMergedByteBuffers pendingBuffers = new ReuseableMergedByteBuffers();
  private final ListenerHelper<HTTPRequestCallback> listeners = new ListenerHelper<>(HTTPRequestCallback.class);
  private int maxHeaderLength = MAX_HEADER_LENGTH;
  private HTTPRequest request;
  private int currentBodySize = 0;
  private long bodySize = 0;
  private ByteBuffer chunkedBB;
  private boolean isChunked = false;
  private boolean isWebsocket = false;
  private WSFrame lastFrame = null;

  /**
   * Constructs an httpRequestProcessor.
   */
  public HTTPRequestProcessor() {}
  
  /**
   * Set the maximum sized headers this processor is willing to accept.
   * 
   * @param maxHeaderLength The size in bytes that the header in total can not exceed
   */
  public void setMaxHeaderLength(int maxHeaderLength) {
    ArgumentVerifier.assertGreaterThanZero(maxHeaderLength, "maxHeaderLength");
    
    this.maxHeaderLength = maxHeaderLength;
  }

  /**
   * Adds an {@link HTTPRequestCallback} to the processor.  More the one can be added.
   * 
   * @param hrc the {@link HTTPRequestCallback} to add. 
   */
  public void addHTTPRequestCallback(HTTPRequestCallback hrc) {
    listeners.addListener(hrc);
  }

  /**
   * Removes an {@link HTTPRequestCallback} from the processor. 
   * 
   * @param hrc the {@link HTTPRequestCallback} to remove. 
   */
  public void removeHTTPRequestCallback(HTTPRequestCallback hrc) {
    listeners.removeListener(hrc);
  }

  /**
   * {@link ByteBuffer} to send through the processor.
   * 
   * @param bb {@link ByteBuffer} to send through the processor.
   */
  public void processData(ByteBuffer bb) {
    pendingBuffers.add(bb);
    runProcessData();
  }

  /**
   * {@link MergedByteBuffers} to send through the processor.
   * 
   * @param bb {@link MergedByteBuffers} to send through the processor.
   */
  public void processData(MergedByteBuffers bb) {
    pendingBuffers.add(bb);
    runProcessData();
  }

  /**
   * Called when an http request connection is closes.  Some types of requests are only completed when the connection is closed (http1.0).
   * 
   * This will finish all the callback and then reset the processor to be able to be reused if wanted.
   * 
   */
  public void connectionClosed() {
    if(request != null) {
      if(!isChunked && bodySize >=0 && bodySize != currentBodySize) {
        reset(new HTTPParsingException("Body was not completed!"));
      } else {
        reset();
      }
    }
  }
  
  /**
   * Resets the processor and any pending buffers left in it.
   */
  public void clearBuffer() {
    reset();
    this.pendingBuffers.discard(this.pendingBuffers.remaining());
  }

  private void runProcessData() {
    while(pendingBuffers.remaining() > 0) {
      if(request == null) {
        int pos = pendingBuffers.indexOf(HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR);
        if(pos > maxHeaderLength || (pos == -1 && pendingBuffers.remaining() > maxHeaderLength)) {
          reset(new HTTPParsingException("Headers are to big!"));
          return;
        }
        if(pos > -1) {
          try{
            int reqDelim = pendingBuffers.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
            if(reqDelim >= MAX_HEADER_ROW_LENGTH) {
              reset(new HTTPParsingException("Request Header is to big!"));
              return;    
            }
            String reqh = pendingBuffers.getAsString(reqDelim);
            HTTPRequestHeader hrh = new HTTPRequestHeader(reqh);
            HTTPHeaders hh = new HTTPHeaders(pendingBuffers.getAsString((pos + 2) - reqh.length()));
            pendingBuffers.discard(2);  // discard final newline
            request = new HTTPRequest(hrh, hh);
            listeners.call().headersFinished(request);
            bodySize = hh.getContentLength();
            String upgrade = hh.getHeader(HTTPConstants.HTTP_KEY_UPGRADE);
            if(hh.isChunked()) {
              bodySize = -1;
              isChunked = true;
            } else if(upgrade != null && upgrade.equals(HTTPConstants.WEBSOCKET)) {
              bodySize = -1;
              isWebsocket = true;
            } else {
              if(bodySize <= 0) {
                reset();
              }
            }
          } catch (Exception e) {
            reset(e);
            return;
          }
        } else {
          break;
        }
      } else if(!processBody()) {
        return;
      }
    }
  }

  private boolean processBody() {
    if(isChunked) {
      return parseChunkData();
    } else if(isWebsocket) {
      return parseWebsocketData();
    } else {
      return parseStreamBody();
    }
  }

  private boolean parseWebsocketData() {
    if(lastFrame == null) {
      try {
        lastFrame = WSFrame.parseWSFrame(pendingBuffers);
      } catch(ParseException e) {
        return false;
      }
    }
    if(lastFrame.getPayloadDataLength() <= pendingBuffers.remaining()) {
      ByteBuffer bb = pendingBuffers.pullBuffer((int)lastFrame.getPayloadDataLength());
      if(lastFrame.hasMask()) {
        bb = WSUtils.maskData(bb, lastFrame.getMaskValue());
      }
      for(HTTPRequestCallback hrc: listeners.getSubscribedListeners()) {
        hrc.websocketData(lastFrame, bb.duplicate());
      }
      lastFrame = null;
    }
    if(pendingBuffers.remaining() >= 2) {
      return true;
    }
    return false;
  }

  private boolean parseStreamBody() {
    if(bodySize == -1) {
      sendDuplicateBBtoListeners(pendingBuffers.pullBuffer(pendingBuffers.remaining()));
      return false;
    } else {
      if(currentBodySize < bodySize) {
        ByteBuffer bb = pendingBuffers.pullBuffer((int)Math.min(pendingBuffers.remaining(), 
                                                                bodySize - currentBodySize));
        currentBodySize+=bb.remaining();
        sendDuplicateBBtoListeners(bb);
        if(currentBodySize == bodySize) {
          reset();
        }
        return true;
      } else {
        return false;
      }
    }
  }

  private boolean parseChunkData() {
    if(bodySize < 0) {
      int pos = pendingBuffers.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
      try {
        if(pos > 0) {
          bodySize = Integer.parseInt(pendingBuffers.getAsString(pos), HTTPConstants.HEX_SIZE);
          pendingBuffers.discard(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length());
          if(bodySize == 0) {
            pendingBuffers.discard(HTTPConstants.HTTP_NEWLINE_DELIMINATOR.length());
            reset();
            return false;
          } else {
            // we can int cast safely due to int parse above
            chunkedBB = ByteBuffer.allocate(Math.min((int)bodySize, MAX_CHUNK_SIZE));
            return true;
          }
        } else {
          return false;
        }
      } catch(Exception e) {
        listeners.call().hasError(new HTTPParsingException("Problem reading chunk size!", e));
        return false;
      }
    } // if not returned we can now try to read
    
    if (currentBodySize == bodySize) {
      if(pendingBuffers.remaining() >=2) {
        if (chunkedBB != null) {
          if (chunkedBB.position() > 0) {
            chunkedBB.flip();
            sendDuplicateBBtoListeners(chunkedBB);
          }
          chunkedBB = null;
        }
        pendingBuffers.discard(2);
        bodySize = -1;
        currentBodySize = 0;
      } else {  // waiting for chunk termination
        return false;
      }
    } else {
      int read = pendingBuffers.get(chunkedBB.array(), chunkedBB.position(), chunkedBB.remaining());
      chunkedBB.position(chunkedBB.position() + read);
      currentBodySize += read;
      
      if (! chunkedBB.hasRemaining()) {
        chunkedBB.flip();
        sendDuplicateBBtoListeners(chunkedBB);
        int remaining = Math.min(((int)bodySize) - currentBodySize, MAX_CHUNK_SIZE);
        if (remaining > 0) {
          chunkedBB.clear();
          if (remaining < chunkedBB.capacity()) {
            chunkedBB.limit(remaining);
          } // it should not be possible for remaining to be larger than the buffer size
        } else {
          chunkedBB = null;
        }
      }
    }
    return true;
  }

  private void sendDuplicateBBtoListeners(ByteBuffer bb) {
    for(HTTPRequestCallback hrc: listeners.getSubscribedListeners()) {
      hrc.bodyData(bb.duplicate());
    }
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
    if(t != null) {
      this.listeners.call().hasError(t);
    } else if(this.request != null) {
      this.listeners.call().finished();
    }
    this.request = null;
    this.currentBodySize = 0;
    this.bodySize = 0;
    if (isChunked) {
      isChunked = false;
      chunkedBB = null;
    }
  }

  /**
   * Lets you know if we have gotten enough data to see a full request yet.
   * 
   * @return true if the request is completed false if not.
   */
  public boolean isRequestComplete() {
    return request != null;
  }

  /**
   * returns the total amount of unprocessable data pending.
   * 
   * @return the size of the pending unprocessed buffers.
   */
  public int getBufferSize() {
    return this.pendingBuffers.remaining();
  }

  /**
   * Used for processing data with {@link HTTPRequestProcessor}.
   * 
   * @author lwahlmeier
   *
   */
  public interface HTTPRequestCallback {

    /**
     * Called when we reach a completed {@link HTTPRequest} header.
     * 
     * @param hr the completed {@link HTTPRequest}.
     */
    public void headersFinished(HTTPRequest hr);

    /**
     * Called after {@link #headersFinished(HTTPRequest)} is called for any body associated with the request.
     * 
     * If the body is chunked this will be called per chunk of data and the chunk header is not included.
     * 
     * @param bb the body in a {@link ByteBuffer}
     */
    public void bodyData(ByteBuffer bb);

    /**
     * If the last headersFinished was a websocket request this will be called back on each frame we get from processed data.
     * 
     * @param wsf The {@link WSFrame} that was wrapping the data.
     * @param bb the payload of the frame, it will be unmasked already if its needed.
     */
    public void websocketData(WSFrame wsf, ByteBuffer bb);

    /**
     * This is called when the http request finishes.  This can also be called if the connection is set to closed, or reset manually.
     * 
     */
    public void finished();

    /**
     * This is called if there is a parsing error or the connection closes before finished, or any other error reason.
     * after this is called a new {@link #headersFinished(HTTPRequest)} can still be called.
     * 
     * @param t the error that occurred.
     */
    public void hasError(Throwable t);
  }
}
