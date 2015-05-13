package org.threadly.litesockets.protocol.http.structures;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

import org.threadly.litesockets.protocol.http.HTTPConstants;
import org.threadly.litesockets.utils.MergedByteBuffers;

public class HTTPResponseProcessor {
  private final MergedByteBuffers buffers = new MergedByteBuffers();
  private final ArrayDeque<ByteBuffer> chunkedBuffers = new ArrayDeque<ByteBuffer>(); 
  protected HTTPResponseHeader rHeader;
  private HTTPHeaders headers;
  private int bodyLength = -1;
  private int nextChunk = -1;
  private boolean chunked = false;
  private boolean parseChunks = true;
  private byte[] body = new byte[0];
  

  private boolean errors = false;
  private String errorText = null;
  private boolean isDone = false;

  public boolean process(MergedByteBuffers bb) {
    if(isDone) {
      throw new IllegalArgumentException("Response is complete can not process any more buffers!");
    }
    buffers.add(bb);
    int eor = buffers.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
    if(rHeader == null && eor >= 0) {
      try {        
        rHeader = new HTTPResponseHeader(buffers.getAsString(eor));
      } catch(Exception e) {
        errors = true;
        errorText = "Could not parse Response Header! "+e.getMessage();
      }
      buffers.discard(2);
    } else if(rHeader == null && buffers.remaining() > 500) {
      errors = true;
      errorText = "Could not parse Headers!";
    } else if(rHeader == null){
      return false;
    }
    
    int eoh = buffers.indexOf(HTTPConstants.HTTP_DOUBLE_NEWLINE_DELIMINATOR);
    if(headers == null && eoh > 0) {
      headers = new HTTPHeaders(buffers.getAsString(eoh));
      bodyLength = headers.getContentLength();
      chunked = headers.isChunked();
      buffers.discard(4);
    } else if(headers == null && buffers.remaining() > 50000) {
      errors = true;
      errorText = "Could not parse Headers!";
    } else if (headers == null) {
      return false;
    }
    
    if(headers != null) {
      checkBody();
    }
    return isDone;
  }
  
  public boolean hasBodyLength() {
    return this.bodyLength > 0;
  }
  
  public void disableChunkParser() {
    this.parseChunks = false;
  }
  
  public boolean isDone() {
    return isDone;
  }
  
  public boolean headersDone() {
    return headers != null;
  }
  
  public boolean isError() {
    return errors;
  }
  
  public String getErrorText() {
    return errorText;
  }
  
  public ByteBuffer remainingBuffer() {
    if(!isDone || !errors) {
      throw new IllegalStateException("Can not get remaining Buffers unless Processor is Done!");
    }
    return buffers.pull(buffers.remaining());
  }
  
  public int getBodyLength() {
    return bodyLength;
  }
  
  public void doClose() {
    if(!isDone && bodyLength == -1 && buffers.remaining() > 0) {
      bodyLength = buffers.remaining();
      body = new byte[bodyLength];
      buffers.get(body);
      isDone = true;
    }
  }

  private void checkBody() {

    if(! chunked) {
      if(headers.getContentLength() >= 0) {
        if(buffers.remaining() >= bodyLength) {
          body = new byte[bodyLength];
          buffers.get(body);
        }
      }
      if(body != null && body.length == bodyLength) {
        isDone = true;
      }
      if(isDone && buffers.remaining() > 0) {
        errors = true;
      }
    } else if(parseChunks){
      int eor = buffers.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
      while((nextChunk > 0 && nextChunk <= buffers.remaining()) || (nextChunk < 0 && eor >= 0)) {
        if(nextChunk > 0 && nextChunk <= buffers.remaining()) {
          ByteBuffer newBody = ByteBuffer.allocate(nextChunk+body.length);
          newBody.put(body);
          ByteBuffer bb = buffers.pull(nextChunk);
          newBody.put(bb);
          body = newBody.array();
          nextChunk = -1;
          eor = buffers.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
        }
        if(nextChunk < 0 && eor > 0) {
          String tmp = buffers.getAsString(eor);
          buffers.discard(2);
          nextChunk = Integer.parseInt(tmp, 16);
        } else if(eor == 0 && nextChunk < 0) {
          buffers.discard(2);
          eor = buffers.indexOf(HTTPConstants.HTTP_NEWLINE_DELIMINATOR);
        }
      }
      if(nextChunk == 0) {
        bodyLength = body.length;
        isDone = true;
      }
    }
  }
  
  public ByteBuffer pullBody() {
    ByteBuffer bb = ByteBuffer.wrap(body);
    body = new byte[0];
    bodyLength = -1;
    return bb;
  }
  
  public ByteBuffer getExtraBuffers() {
    return buffers.pull(buffers.remaining());
  }
  
  public boolean isChunked() {
    return chunked;
  }
  
  public HTTPResponse getResponse() {
    if(!isError()  && isDone) {
      return new HTTPResponse(rHeader, headers, body);
    }
    throw new IllegalStateException("Can not make response from incomplete or Errored data");
  }
  
  public HTTPResponse getResponseHeadersOnly() {
    if(headersDone() && ! isError()) {
      return new HTTPResponse(rHeader, headers, new byte[0]);
    }
    throw new IllegalStateException("Can not make response from incomplete or Errored data");
  }
}
