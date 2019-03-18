package org.threadly.litesockets.protocols.websocket;

/**
 * Standard Websocket OpCodes.
 */
public enum WSOPCode {
  Continuation((byte)0), Text((byte)1), Binary((byte)2),
  Close((byte)8), Ping((byte)9), Pong((byte)10); 
  
  private final byte value;
  
  private WSOPCode(byte value) {
    this.value = value;
  }
  
  /**
   * Get the opcode value.
   * 
   * @return The standard value associated with the opcode
   */
  public byte getValue() {
    return value;
  }
  
  /**
   * Convert a op code byte to an {@link WSOPCode} enum value.
   * 
   * @param b Byte to check against
   * @return Matching opcode or {@code null} if none was found
   */
  public static WSOPCode fromByte(byte b) {
    for(WSOPCode oc: WSOPCode.values()) {
      if(oc.getValue() == b) {
        return oc;
      }
    }
    return null;
  }
}