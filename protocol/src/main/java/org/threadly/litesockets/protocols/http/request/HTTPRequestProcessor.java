package org.threadly.litesockets.protocols.http.request;

import java.nio.ByteBuffer;

import org.threadly.concurrent.event.ListenerHelper;
import org.threadly.litesockets.utils.MergedByteBuffers;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;
import org.threadly.litesockets.protocols.http.shared.HTTPParsingException;

public class HTTPRequestProcessor {
  private final MergedByteBuffers pendingBuffers = new MergedByteBuffers();
  private final ListenerHelper<HTTPRequestCallback> listeners = ListenerHelper.build(HTTPRequestCallback.class);
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
        if(pos > -1) {
          MergedByteBuffers tmp = new MergedByteBuffers();
          tmp.add(pendingBuffers.pull(pos+2));
          pendingBuffers.discard(2);
          try{
            HTTPRequestHeader hrh = new HTTPRequestHeader(tmp.getAsString(tmp.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR)));
            HTTPHeaders hh = new HTTPHeaders(tmp.getAsString(tmp.remaining()));
            request = new HTTPRequest(hrh, hh);
            bodySize = hh.getContentLength();
            if(bodySize == -1) {
              String te = hh.getHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING);
              if(te != null && te.equalsIgnoreCase("chunked")) {
                bodySize = -1;
                isChunked = true;
              }
            }
            listeners.call().headersFinished(request);
            if(!isChunked && bodySize == 0) {
              reset();
            }
          } catch (Exception e) {
            reset(e);
            return;
          }
        } else {
          break;
        }
      } else {
        processBody();
      }
    }
  }

  private void processBody() {
    while(pendingBuffers.remaining() > 0 && pendingBuffers.remaining() >= bodySize) {
      if(isChunked) {
        parseChunkData();
      } else {
        parseStreamBody();
      }
    }
  }
  
  public void parseStreamBody() {
    if(bodySize == -1) {
      sendDuplicateBBtoListeners(pendingBuffers.pull(pendingBuffers.remaining()));
    } else {
      if(currentBodySize < bodySize) {
        ByteBuffer bb = pendingBuffers.pull(Math.min(pendingBuffers.remaining(), bodySize - currentBodySize));
        currentBodySize+=bb.remaining();
        sendDuplicateBBtoListeners(bb);
        if(currentBodySize == bodySize && !isChunked) {
          reset();
          if(pendingBuffers.remaining() > 0) {
            runProcessData();
          }
        }
      }
    }
  }
  
  public void parseChunkData() {
    while(bodySize > 0 || pendingBuffers.remaining() > 0) {
      if(bodySize < 0) {
        int pos = pendingBuffers.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
        try {
          if(pos > 0) {
            bodySize = Integer.parseInt(pendingBuffers.getAsString(pos), 16);
            pendingBuffers.discard(2);
            if(bodySize == 0) {
              reset();
              return;
            } else {
              chunkedBB = ByteBuffer.allocate(bodySize);
            }
          } else {
            return;
          }
        } catch(Exception e) {
          listeners.call().hasError(new HTTPParsingException("Problem reading chunk size!", e));
        }
      } else {
        if(currentBodySize == bodySize && pendingBuffers.remaining() >=2) {
          chunkedBB.flip();
          sendDuplicateBBtoListeners(chunkedBB.duplicate());
          chunkedBB = null;
          pendingBuffers.discard(2);
          bodySize = -1;
          currentBodySize = 0;
        } else {
          ByteBuffer bb = pendingBuffers.pull(Math.min(pendingBuffers.remaining(), bodySize - currentBodySize));
          currentBodySize+=bb.remaining();
          chunkedBB.put(bb);
        }
      }
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

  public static interface HTTPRequestCallback {
    public void headersFinished(HTTPRequest hr);
    public void bodyData(ByteBuffer bb);
    public void finished();
    public void hasError(Throwable t);
  }
}
