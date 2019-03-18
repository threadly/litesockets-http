package org.threadly.litesockets.protocols.websocket;

/**
 * Constants used for websockets.
 */
public class WSConstants {
  public static final String MAGIC_UUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"; 
  public static final int DEFAULT_SECRET_KEY_SIZE = 20;
  public static final String DEFAULT_SECRET_HASH_ALGO = "SHA-1";
  public static final int UNSIGN_BYTE_MASK = 0xff;
  public static final int UNSIGNED_SHORT_MASK = 0xffff;
  public static final int OPCODE_MASK = 0xf;
  public static final int WS_SMALL_LENGTH_MASK = 0x7f;
  public static final int WS_SHORT_SIZE = 126;
  public static final int WS_LONG_SIZE = 127;
  public static final int WS_SHORT_LENGTH = 2;
  public static final int WS_LONG_LENGTH = 8;
  public static final int MASK_SIZE = 4;
  public static final int MIN_WS_FRAME_SIZE = 2;
  public static final int MAX_WS_FRAME_SIZE = 14;
  public static final int STATIC_FOUR = 4;
  public static final int STATIC_FIVE = 5;
  public static final int STATIC_SIX = 6;
  public static final int STATIC_SEVEN = 7;
}
