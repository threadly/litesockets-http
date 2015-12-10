package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;

import org.threadly.concurrent.event.ListenerHelper;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;
import org.threadly.litesockets.utils.MergedByteBuffers;

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
  
  private final MergedByteBuffers pendingBuffers = new MergedByteBuffers();
  private final ListenerHelper<HTTPRequestCallback> listeners = ListenerHelper.build(HTTPRequestCallback.class);
  private int maxHeaderLength = MAX_HEADER_LENGTH;
  private int maxRowLength = MAX_HEADER_ROW_LENGTH;
  private HTTPRequest request;
  private int currentBodySize = 0;
  private int bodySize = 0;
  private ByteBuffer chunkedBB;
  private boolean isChunked = false;

  public HTTPRequestProcessor() {

  }

  public void addHTTPRequestCallback(HTTPRequestCallback hrc) {
    listeners.addListener(hrc);
  }

  public void removeHTTPRequestCallback(HTTPRequestCallback hrc) {
    listeners.removeListener(hrc);
  }

  public void processData(byte[] ba) {
    processData(ByteBuffer.wrap(ba));
  }

  public void processData(ByteBuffer bb) {
    pendingBuffers.add(bb);
    runProcessData();
  }
  
  public void processData(MergedByteBuffers bb) {
    pendingBuffers.add(bb);
    runProcessData();
  }

  public void connectionClosed() {
    if(request != null) {
      if(!isChunked && bodySize >=0 && bodySize != currentBodySize) {
        reset(new HTTPParsingException("Body was not completed!"));
      } else {
        reset();
      }
    }
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
          MergedByteBuffers tmp = new MergedByteBuffers();
          tmp.add(pendingBuffers.pull(pos+2));
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
            if(hh.isChunked()) {
              bodySize = -1;
              isChunked = true;              
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
    } else {
      return parseStreamBody();
    }
  }
  
  private boolean parseStreamBody() {
    if(bodySize == -1) {
      sendDuplicateBBtoListeners(pendingBuffers.pull(pendingBuffers.remaining()));
      return false;
    } else {
      if(currentBodySize < bodySize) {
        ByteBuffer bb = pendingBuffers.pull(Math.min(pendingBuffers.remaining(), bodySize - currentBodySize));
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
              chunkedBB = ByteBuffer.allocate(bodySize);
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
          ByteBuffer bb = pendingBuffers.pull(Math.min(pendingBuffers.remaining(), bodySize - currentBodySize));
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

  public void reset() {
    reset(null);
  }
  
  public void reset(Throwable t) {
    if(t == null) {
      this.listeners.call().finished();
    } else {
      this.listeners.call().hasError(t);
    }
    this.request = null;
    this.currentBodySize = 0;
    this.bodySize = 0;
    this.isChunked = false;
  }

  public boolean isRequestComplete() {
    return request != null;
  }

  /**
   * Used for processing data with {@link HTTPRequestProcessor}.
   * 
   * @author lwahlmeier
   *
   */
  public interface HTTPRequestCallback {
    public void headersFinished(HTTPRequest hr);
    public void bodyData(ByteBuffer bb);
    public void finished();
    public void hasError(Throwable t);
  }
}
