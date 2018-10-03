package org.threadly.litesockets.protocols.ws;

/**
 * Standard Websocket OpCodes.
 * 
 * @author lwahlmeier
 *
 */
public enum WSOPCode {
  Continuation((byte)0), Text((byte)1), Binary((byte)2),
  Close((byte)8), Ping((byte)9), Pong((byte)10); 
  
  private final byte value;
  WSOPCode(byte value) {
    this.value = value;
  }
  
  public byte getValue() {
    return value;
  }
}