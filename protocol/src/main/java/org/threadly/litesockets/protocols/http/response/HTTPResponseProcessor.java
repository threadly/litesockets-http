package org.threadly.litesockets.protocols.http.response;

import java.nio.ByteBuffer;
import java.util.HashMap;

import org.threadly.concurrent.event.ListenerHelper;
import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.buffers.ReuseableMergedByteBuffers;
import org.threadly.litesockets.buffers.SimpleMergedByteBuffers;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;


/**
 * This is a simple HTTP Response parser.  It takes in Bytes and builds up an {@link HTTPResponse} object. 
 */
public class HTTPResponseProcessor {
  public static final int MAX_RESPONSE_HEADER_SIZE = 500;
  public static final int MAX_HEADER_SIZE = 50000;
  private final ReuseableMergedByteBuffers buffers = new ReuseableMergedByteBuffers();
  private final ListenerHelper<HTTPResponseCallback> listeners = new ListenerHelper<HTTPResponseCallback>(HTTPResponseCallback.class);
  private HTTPResponse response;
  private int nextChunkSize = -1;
  private int currentBodySize = 0;
  
  public HTTPResponseProcessor() {}
  
  public void addHTTPRequestCallback(HTTPResponseCallback hrc) {
    listeners.addListener(hrc);
  }

  public void removeHTTPRequestCallback(HTTPResponseCallback hrc) {
    listeners.removeListener(hrc);
  }
  
  public void processData(byte[] ba) {
    processData(ByteBuffer.wrap(ba));
  }
  
  public void processData(ByteBuffer bb) {
    processData(new SimpleMergedByteBuffers(false, bb));
  }
  
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
          reset(null);
        }
      } catch(Exception e) {
        reset(e);
      }
    }
    
    if(response != null && buffers.remaining() > 0) {
      processBody();
    }
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
      } else if (response.getHeaders().getContentLength() == -1) {
        sendDuplicateBBtoListeners(buffers.pullBuffer(buffers.remaining()));
      }
    }
  }
  
  public int getCurrentBodySize() {
    return this.currentBodySize;
  }
  
  public boolean isProcessing() {
    if(response != null || buffers.remaining() > 0) {
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
            nextChunkSize = Integer.parseInt(buffers.getAsString(pos), HTTPConstants.HEX_SIZE);
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
  
  public void resetAllState() {
    listeners.clearListeners();
    buffers.discard(buffers.remaining());
    reset(null);
  }
  
  private void reset(Throwable t) {
    response = null;
    currentBodySize = 0;
    nextChunkSize = -1;
    if(t == null) {
      listeners.call().finished();
    } else {
      listeners.call().hasError(t);
    }
  }
  
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
    public void headersFinished(HTTPResponse hr);
    public void bodyData(ByteBuffer bb);
    public void finished();
    public void hasError(Throwable t);
  }
}
