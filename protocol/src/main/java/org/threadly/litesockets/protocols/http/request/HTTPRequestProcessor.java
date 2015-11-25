package org.threadly.protocols.http.request;

import java.nio.ByteBuffer;

import org.threadly.concurrent.event.ListenerHelper;
import org.threadly.litesockets.utils.MergedByteBuffers;
import org.threadly.protocols.http.shared.HTTPConstants;
import org.threadly.protocols.http.shared.HTTPHeaders;
import org.threadly.protocols.http.shared.HTTPParsingException;

public class HTTPRequestProcessor {
  private final MergedByteBuffers pendingBuffers = new MergedByteBuffers();
  private final ListenerHelper<HTTPRequestCallback> listeners = ListenerHelper.build(HTTPRequestCallback.class);
  private HTTPRequest request;
  private int currentBodySize = 0;
  private int bodySize = 0;
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
      if(!isChunked && bodySize != currentBodySize) {
        reset(new HTTPParsingException("Body was not completed!"));
      } else {
        reset();
      }
    }
  }

  private void runProcessData() {
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
              bodySize = 0;
              this.isChunked = true;
            }
          }
          listeners.call().headersFinished(request);
          if(!isChunked && bodySize == 0) {
            listeners.call().finished();
            reset();
          }
        } catch (Exception e) {
          reset(e);
          return;
        }

      }
    } else {
      processBody();
    }
  }

  private void processBody() {
    if(isChunked) {

    } else {
      if(bodySize == -1) {
        listeners.call().bodyData(pendingBuffers.pull(pendingBuffers.remaining()).duplicate());
      } else {
        if(currentBodySize < bodySize) {
          ByteBuffer bb = pendingBuffers.pull(Math.min(pendingBuffers.remaining(), bodySize - currentBodySize));
          currentBodySize+=bb.remaining();
          listeners.call().bodyData(bb.duplicate());
          if(currentBodySize == bodySize) {
            listeners.call().finished();
            reset();
            if(pendingBuffers.remaining() > 0) {
              runProcessData();
            }
          }
        }
      }
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
