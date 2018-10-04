package org.threadly.litesockets.protocols.websocket;

/**
 * Standard Websocket OpCodes.
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
  
  public static WSOPCode fromByte(byte b) {
    for(WSOPCode oc: WSOPCode.values()) {
      if(oc.getValue() == b) {
        return oc;
      }
    }
    return null;
  }
}