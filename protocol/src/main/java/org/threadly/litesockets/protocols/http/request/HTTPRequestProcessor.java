package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;
import java.text.ParseException;

import org.threadly.concurrent.event.ListenerHelper;
import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.buffers.ReuseableMergedByteBuffers;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;
import org.threadly.litesockets.protocols.ws.WebSocketFrameParser;
import org.threadly.litesockets.protocols.ws.WebSocketFrameParser.WebSocketFrame;

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

  private final ReuseableMergedByteBuffers pendingBuffers = new ReuseableMergedByteBuffers();
  private final ListenerHelper<HTTPRequestCallback> listeners = new ListenerHelper<>(HTTPRequestCallback.class);
  private int maxHeaderLength = MAX_HEADER_LENGTH;
  private int maxRowLength = MAX_HEADER_ROW_LENGTH;
  private HTTPRequest request;
  private int currentBodySize = 0;
  private long bodySize = 0;
  private ByteBuffer chunkedBB;
  private boolean isChunked = false;
  private boolean isWebsocket = false;
  private WebSocketFrame lastFrame = null;

  /**
   * Constructs an httpRequestProcessor.
   */
  public HTTPRequestProcessor() {}

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
   * 
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
          MergedByteBuffers tmp = new ReuseableMergedByteBuffers();
          tmp.add(pendingBuffers.pullBuffer(pos+2));
          pendingBuffers.discard(2);
          try{
            String reqh = tmp.getAsString(tmp.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR));
            if(reqh.length() > this.maxRowLength) {
              reset(new HTTPParsingException("Request Header is to big!"));
              return;    
            }
            HTTPRequestHeader hrh = new HTTPRequestHeader(reqh);
            HTTPHeaders hh = new HTTPHeaders(tmp.getAsString(tmp.remaining()));
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
      } else {
        if(!processBody()) {
          return;
        }
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
        lastFrame = WebSocketFrameParser.parseWebSocketFrame(pendingBuffers);
      } catch(ParseException e) {
        return false;
      }
    }
    if(lastFrame.getPayloadDataLength() <= pendingBuffers.remaining()) {
      ByteBuffer bb = pendingBuffers.pullBuffer((int)lastFrame.getPayloadDataLength());
      if(lastFrame.hasMask()) {
        bb = WebSocketFrameParser.doDataMask(bb, lastFrame.getMaskValue());
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
        ByteBuffer bb = pendingBuffers.pullBuffer((int)Math.min(pendingBuffers.remaining(), bodySize - currentBodySize));
        currentBodySize+=bb.remaining();
        sendDuplicateBBtoListeners(bb);
        if(currentBodySize == bodySize) {
          reset();
          return true;
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
            chunkedBB = ByteBuffer.allocate((int)bodySize); // we can int cast safely due to int parse above
            return true;
          }
        } else {
          return false;
        }
      } catch(Exception e) {
        listeners.call().hasError(new HTTPParsingException("Problem reading chunk size!", e));
        return false;
      }
    } else {
      if(currentBodySize == bodySize && pendingBuffers.remaining() >=2) {
        chunkedBB.flip();
        sendDuplicateBBtoListeners(chunkedBB.duplicate());
        chunkedBB = null;
        pendingBuffers.discard(2);
        bodySize = -1;
        currentBodySize = 0;
      } else if(currentBodySize == bodySize && pendingBuffers.remaining() < 2) {
        return false;
      } else {
        ByteBuffer bb = pendingBuffers.pullBuffer((int)Math.min(pendingBuffers.remaining(), bodySize - currentBodySize));
        currentBodySize+=bb.remaining();
        chunkedBB.put(bb);
      }
      return true;
    }
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
    if(this.request != null && t == null) {
      this.listeners.call().finished();
    }
    if(t != null) {
      this.listeners.call().hasError(t);
    }
    this.request = null;
    this.currentBodySize = 0;
    this.bodySize = 0;
    this.isChunked = false;
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
     * after this is called a new {@link #headersFinished(HTTPRequest)} can still be called.
     * 
     * @param t the error that occurred.
     */
    public void hasError(Throwable t);
  }
}
