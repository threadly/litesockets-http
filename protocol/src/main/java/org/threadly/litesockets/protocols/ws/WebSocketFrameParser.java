package org.threadly.litesockets.protocols.ws;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Base64;
import java.util.Random;

import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.buffers.SimpleMergedByteBuffers;


/**
 * Simple frame parser for websockets.
 * 
 * @author lwahlmeier
 *
 */
public class WebSocketFrameParser {
  public static final String MAGIC_UUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"; 
  
  private static final byte[] MAGIC_UUID_BA = MAGIC_UUID.getBytes();
  private static final int UNSIGN_BYTE_MASK = 0xff;
  private static final int UNSIGNED_SHORT_MASK = 0xffff;
  private static final int OPCODE_MASK = 0xf;
  private static final int WS_SMALL_LENGTH_MASK = 0x7f;
  private static final int WS_SHORT_SIZE = 126;
  private static final int WS_LONG_SIZE = 127;
  private static final int WS_SHORT_LENGTH = 2;
  private static final int WS_LONG_LENGTH = 8;
  private static final int MASK_SIZE = 4;
  private static final int MIN_WS_FRAME_SIZE = 2;
  private static final int MAX_WS_FRAME_SIZE = 14;
  private static final int STATIC_FOUR = 4;
  private static final int STATIC_FIVE = 5;
  private static final int STATIC_SIX = 6;
  private static final int STATIC_SEVEN = 7;
  private static final int DEFAULT_SECRET_KEY_SIZE = 20;
  private static final String DEFAULT_SECRET_HASH_ALGO = "SHA-1";
  private static final Random RANDOM = new Random();
  
  
  private WebSocketFrameParser() {}
  
  public static String makeSecretKey() {
    return makeSecretKey(DEFAULT_SECRET_KEY_SIZE);
  }
  
  public static String makeSecretKey(final int size) {
    byte[] ba = new byte[size];
    RANDOM.nextBytes(ba);
    return Base64.getEncoder().encodeToString(ba);
  }

  /**
   * Makes a Sec-WebSocket-Key response string.
   * 
   * @param str base64 string passed in the Sec-WebSocket-Key header.
   * @return a base64 string to set as the Sec-WebSocket-Key response.
   */
  public static String makeKeyResponse(final String str) {
    final MessageDigest md;
    try {
      md = MessageDigest.getInstance(DEFAULT_SECRET_HASH_ALGO);
      md.update(str.getBytes());
      md.update(MAGIC_UUID_BA);
      return Base64.getEncoder().encodeToString(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("HUGE problem we dont support the SHA1 hash cant to websockets!!!!!", e);
    }
  }
  
  public static boolean validateKeyResponse(final String orig, final String response) {
    String correctResponse =  makeKeyResponse(orig);
    return response.equals(correctResponse);
  }

  /**
   * Parses a WebSocket frame out of the provided {@link ByteBuffer}.
   * 
   * @param bb the ByteBuffer containing the WebSocketFrame.
   * @return a {@link WebSocketFrame}.
   * @throws ParseException this is thrown if there is not enough data to make a {@link WebSocketFrame}. 
   */
  public static WebSocketFrame parseWebSocketFrame(final ByteBuffer bb) throws ParseException {
    MergedByteBuffers mbb = new SimpleMergedByteBuffers(false, bb);
    int origSize = mbb.remaining();
    WebSocketFrame wsf = parseWebSocketFrame(mbb);
    bb.position(bb.position() + origSize - mbb.remaining());
    return wsf;
  }
  
  /**
   * Parses a WebSocket frame from the passed in {@link MergedByteBuffers}.  Only the data for the frame
   * will be parsed out of the ByteBuffer, the payload will remain.
   * 
   * @param mbb {@link MergedByteBuffers} containing the frame as the first bytes.
   * @return a {@link WebSocketFrame} object to get info about the data in the {@link WebSocketFrame}.
   * @throws ParseException this is thrown if there is not enough data to make a {@link WebSocketFrame}.
   */
  public static WebSocketFrame parseWebSocketFrame(final MergedByteBuffers mbb) throws ParseException {
    final int size = getFrameLength(mbb);
    if(size > 0 && mbb.remaining() >= size) {
      ByteBuffer nbb = mbb.pullBuffer(size);
      return new WebSocketFrame(nbb);
    } else {
      throw new ParseException("Not enough data to make a WebSocketFrame", 0);
    }
  }

  /**
   * Gives the total length of the next Frame in the provided {@link MergedByteBuffers}.
   * This does not modify the {@link MergedByteBuffers}.
   * 
   * @param mbb {@link MergedByteBuffers} to get the Frame length from.
   * @return size of the frame in bytes, or -1 is there is not enough data to make figure out the frame length.
   */
  public static int getFrameLength(final MergedByteBuffers mbb) {
    final MergedByteBuffers nmbb = mbb.duplicate();
    return getFrameLength(nmbb.pullBuffer(Math.min(nmbb.remaining(), MAX_WS_FRAME_SIZE)));
  }
  
  /**
   * Gives the total length of the next Frame in the provided {@link ByteBuffer}.
   * This does not modify the {@link ByteBuffer}.
   * 
   * @param bb the {@link ByteBuffer} to find the frame length on.
   * @return the size of the frame in this {@link ByteBuffer}.
   */
  public static int getFrameLength(final ByteBuffer bb) {
    if(bb.remaining() < MIN_WS_FRAME_SIZE) {
      return -1;
    }

    int size = MIN_WS_FRAME_SIZE + getLengthSize(bb);
    if(hasMask(bb)) {
      size += MASK_SIZE;
    } 
    return size;
  }

  /**
   * This will mask or unmask data against provided mask.
   * 
   * @param nbb the {@link ByteBuffer} to apply the mask to.
   * @param mask the mask to apply to the ByteBuffer.
   * @return {@link ByteBuffer} with the provided mask applyed to it.
   */
  public static ByteBuffer doDataMask(final ByteBuffer nbb, final int mask) {
    if(mask == 0) {
      return nbb;
    } else {
      byte[] maskArray = ByteBuffer.allocate(MASK_SIZE).putInt(mask).array();
      ByteBuffer rbb = ByteBuffer.allocate(nbb.remaining());
      while(nbb.remaining()>=MASK_SIZE) {
        rbb.putInt(nbb.getInt()^mask);
      }
      for(int i=0; nbb.remaining() > 0; i++) {
        rbb.put((byte)(nbb.get()^maskArray[i%MASK_SIZE]));
      }
      rbb.flip();
      return rbb;
    }
  }

  public static WebSocketFrame makeWebSocketFrame(final int size, final WebSocketOpCode opCode, final boolean mask) {
    return makeWebSocketFrame(size, true, opCode.getValue(), mask);
  }
  
  /**
   * Creates a {@link WebSocketFrame} object with the provided parameters.
   * 
   * @param size the size of the payload in the WebSocket Frame.
   * @param opCode The opCode to put in this WebSocket.
   * @param mask true if a mask should be added to this frame, false if not.
   * @return a {@link WebSocketFrame} object created with the provided params.
   */
  public static WebSocketFrame makeWebSocketFrame(final int size, final byte opCode, final boolean mask) {
    return makeWebSocketFrame(size, true, opCode, mask);
  }
  
  
  public static WebSocketFrame makeWebSocketFrame(final int size, boolean isFinished, WebSocketOpCode opCode, final boolean mask) {
    return makeWebSocketFrame(size, isFinished, opCode.getValue(), mask);
  }
  /**
   * Creates a {@link WebSocketFrame} object with the provided parameters.
   * 
   * @param size the size of the payload in the WebSocket Frame.
   * @param isFinished true if we should mark this WebSocket Frame as finished false if not.
   * @param opCode The opCode to put in this WebSocket.
   * @param mask true if a mask should be added to this frame, false if not.
   * @return a {@link WebSocketFrame} object created with the provided params.
   */
  public static WebSocketFrame makeWebSocketFrame(final int size, boolean isFinished, byte opCode, final boolean mask) {

    ByteBuffer nbb;
    int maskExtra = mask ? MASK_SIZE : 0;
    byte bmask = mask ? (byte)1 : (byte)0;
    byte firstByte = opCode;
    if(isFinished) {
      firstByte = (byte)(firstByte | (1<<STATIC_SEVEN));
    }
    
    if(size < WS_SHORT_SIZE) {
      nbb = ByteBuffer.allocate(MIN_WS_FRAME_SIZE+maskExtra);
      nbb.put(firstByte);
      nbb.put((byte)(bmask<<STATIC_SEVEN | size));
    } else if (size <= UNSIGNED_SHORT_MASK) {
      nbb = ByteBuffer.allocate(MIN_WS_FRAME_SIZE+WS_SHORT_LENGTH+maskExtra);
      nbb.put(firstByte);
      nbb.put((byte)(bmask<<STATIC_SEVEN|WS_SHORT_SIZE));
      nbb.putShort((short)size);
    } else {
      nbb = ByteBuffer.allocate(MIN_WS_FRAME_SIZE+WS_LONG_LENGTH+maskExtra);
      nbb.put(firstByte);
      nbb.put((byte)(bmask<<STATIC_SEVEN|WS_LONG_SIZE));
      nbb.putLong(size);
    }

    if(mask) {
      nbb.putInt(RANDOM.nextInt());
    }
    nbb.flip();
    return new WebSocketFrame(nbb);
  }

  private static byte getSmallLen(final ByteBuffer bb) {
    return (byte)(bb.get(1) & WS_SMALL_LENGTH_MASK);            
  }

  private static int getLengthSize(final ByteBuffer bb) {
    final byte sl = getSmallLen(bb);
    if(sl == WS_SHORT_SIZE) {
      return WS_SHORT_LENGTH;
    } else if(sl == WS_LONG_SIZE) {
      return WS_LONG_LENGTH;
    }
    return 0;
  }

  private static boolean hasMask(final ByteBuffer bb) {
    return (bb.get(1) & UNSIGN_BYTE_MASK) >> STATIC_SEVEN == 1;
  }



  /**
   * WebSocketFrame object.  This is allows you to easily get information about the WebSocketFrame data.
   * This object is immutable.
   * 
   * 
   * @author lwahlmeier
   *
   */
  public static class WebSocketFrame {
    private final ByteBuffer bb;
    private final int frameLength;

    protected WebSocketFrame(final ByteBuffer bb) {
      frameLength = getFrameLength(bb);
      if(frameLength < 0 || bb.remaining() < frameLength) {
        throw new IllegalStateException("Not enough data to make a WebSocketFrame");
      }
      this.bb = bb;
    }
    
    public ByteBuffer getRawFrame() {
      return bb.duplicate();
    }

    public boolean isFinished() {
      return ((bb.get(0)&UNSIGN_BYTE_MASK) >> STATIC_SEVEN) == 1;
    }

    public boolean hasRSV1() {
      return ((bb.get(0) >> STATIC_SIX) &0x1) == 1;
    }

    public boolean hasRSV2() {
      return ((bb.get(0) >> STATIC_FIVE) &0x1) == 1;
    }

    public boolean hasRSV3() {
      return ((bb.get(0) >> STATIC_FOUR) &0x1) == 1;
    }

    public int getOpCode() {
      return bb.get(0) & OPCODE_MASK;
    }

    public boolean hasMask() {
      return (bb.get(1) & UNSIGN_BYTE_MASK) >> STATIC_SEVEN == 1;
    }

    public long getPayloadDataLength() {
      byte sl = getSmallLen(bb);
      if(sl < WS_SHORT_SIZE) {
        return sl;
      } else if(sl == WS_SHORT_SIZE) {
        return bb.getShort(2) & UNSIGNED_SHORT_MASK;
      } else {
        return bb.getLong(2);
      }
    }

    public int getMaskValue() {
      if(hasMask()) {
        return bb.getInt(getFrameLength(bb)-MASK_SIZE);
      }
      return 0;
    }

    public byte[] getMaskArray() {
      byte[] ba = new byte[MASK_SIZE];
      if(hasMask()) {
        final int start = getFrameLength(bb)-MASK_SIZE;
        for(int i=0; i<MASK_SIZE; i++) {
          ba[i] = bb.get(start+i);  
        }
        return ba;
      }
      return ba;
    }

    public ByteBuffer unmaskPayload(final ByteBuffer nbb) {
      if(!hasMask()) {
        return nbb;
      } else {
        return doDataMask(nbb, getMaskValue());
      }
    }
  }
}
