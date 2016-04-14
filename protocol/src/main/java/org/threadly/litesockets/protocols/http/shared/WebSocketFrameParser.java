package org.threadly.litesockets.protocols.http.shared;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import org.threadly.litesockets.utils.MergedByteBuffers;

public class WebSocketFrameParser {
  public static final String MAGIC_UUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"; 
  private static final byte[] MAGIC_UUID_BA = MAGIC_UUID.getBytes();
  private static final int WS_SHORT_SIZE = 126;
  private static final int WS_LONG_SIZE = 127;
  private static final int MASK_SIZE = 4;
  private static final int MIN_WS_FRAME_SIZE = 2;
  private static final Random RANDOM = new Random();
  
  public static enum WebSocketOpCodes {
    Continuation((byte)0), Text((byte)1), Binary((byte)2),
    Close((byte)8), Ping((byte)9), Pong((byte)10); 
    
    private final byte value;
    WebSocketOpCodes(byte value) {
      this.value = value;
    }
    
    public byte getValue() {
      return value;
    }
  }

  /**
   * Makes a Sec-WebSocket-Key response string.
   * 
   * @param str base64 string passed in the Sec-WebSocket-Key header.
   * @return a base64 string to set as the Sec-WebSocket-Key response.
   */
  public static String makeKey(final String str) {
    final MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-1");
      md.update(str.getBytes());
      md.update(MAGIC_UUID_BA);
      return Base64.encode(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("HUGE problem we dont support the SHA1 hash cant to websockets!!!!!", e);
    }
  }

  public static WebSocketFrame parseWebSocketFrame(final ByteBuffer bb) {
    MergedByteBuffers mbb = new MergedByteBuffers();
    mbb.add(bb);
    int origSize = mbb.remaining();
    WebSocketFrame wsf = parseWebSocketFrame(mbb);
    bb.position(bb.position() + origSize - mbb.remaining());
    return wsf;
  }
  /**
   * Parses a WebSocketFrame from the passed in MergedByteBuffers.  Only the data for the frame
   * will be parsed out of the ByteBuffer, the payload will remain.
   * 
   * @param mbb MergedByteBuffers containing the frame as the first bytes.
   * @return a WebSocketFrame object to get info about the data in the WebSocketFrame.
   */
  public static WebSocketFrame parseWebSocketFrame(final MergedByteBuffers mbb) {
    final int size = getFrameLength(mbb);
    if(size > 0 && mbb.remaining() >= size) {
      ByteBuffer nbb = mbb.pull(size);
      return new WebSocketFrame(nbb);
    } else {
      throw new IllegalStateException("Not enough data to make a WebSocketFrame");
    }
  }

  /**
   * Gets the length of a the WebSocket frame from a passed in MergedByteBuffers object.
   * This does not modify the MergedByteBuffer.
   * 
   * @param mbb MergedByteBuffer to get the Frame length from.
   * @return size of the frame in bytes, or -1 is there is not enough data to make figure out the frame length.
   */
  public static int getFrameLength(final MergedByteBuffers mbb) {
    final MergedByteBuffers nmbb = mbb.copy();
    return getFrameLength(nmbb.pull(Math.min(nmbb.remaining(), 14)));
  }

  public static ByteBuffer unmaskData(final ByteBuffer nbb, final int MASK) {
    if(MASK == 0) {
      return nbb;
    } else {
      byte[] maskArray = ByteBuffer.allocate(4).putInt(MASK).array();
      ByteBuffer rbb = ByteBuffer.allocate(nbb.remaining());
      while(nbb.remaining()>=MASK_SIZE) {
        rbb.putInt(nbb.getInt()^MASK);
      }
      for(int i=0; nbb.remaining() > 0; i++) {
        rbb.put((byte)(nbb.get()^maskArray[i%MASK_SIZE]));
      }
      rbb.flip();
      return rbb;
    }
  }

  public static int getFrameLength(final ByteBuffer bb) {
    if(bb.remaining() < MIN_WS_FRAME_SIZE) {
      return -1;
    }

    int size = 2 + getLengthSize(bb);
    if(hasMask(bb)) {
      size += MASK_SIZE;
    } 
    return size;
  }
  
  public static WebSocketFrame makeWebSocketFrame(final int size, byte opCode, final boolean mask) {
    return makeWebSocketFrame(size, true, opCode, mask);
  }
  
  public static WebSocketFrame makeWebSocketFrame(final int size, boolean isFinished, byte opCode, final boolean mask) {

    ByteBuffer nbb;
    int maskExtra = mask ? MASK_SIZE : 0;
    byte bmask = mask ? (byte)1 : (byte)0;
    byte firstByte = opCode;
    if(isFinished) {
      firstByte = (byte)(firstByte | (1<<7));
    }
    
    if(size < WS_SHORT_SIZE) {
      nbb = ByteBuffer.allocate(2+maskExtra);
      nbb.put(firstByte);
      nbb.put((byte)(bmask<<7 | size));
    } else if (size <= 65535) {
      nbb = ByteBuffer.allocate(4+maskExtra);
      nbb.put(firstByte);
      nbb.put((byte)(bmask<<7|WS_SHORT_SIZE));
      nbb.putShort((short)size);
    } else {
      nbb = ByteBuffer.allocate(10+maskExtra);
      nbb.put(firstByte);
      nbb.put((byte)(bmask<<7|WS_LONG_SIZE));
      nbb.putLong(size);
    }

    if(mask) {
      nbb.putInt(RANDOM.nextInt());
    }
    nbb.flip();
    return new WebSocketFrame(nbb);
  }

  private static byte getSmallLen(final ByteBuffer bb) {
    return (byte)(bb.get(1) & 0x7f);            
  }

  private static int getLengthSize(final ByteBuffer bb) {
    final byte sl = getSmallLen(bb);
    if(sl == WS_SHORT_SIZE) {
      return 2;
    } else if(sl == WS_LONG_SIZE) {
      return 8;
    }
    return 0;
  }

  private static boolean hasMask(final ByteBuffer bb) {
    return (bb.get(1) &0xff) >> 7 == 1;
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
      return ((bb.get(0)&0xff) >> 7) == 1;
    }

    public boolean hasRSV1() {
      return ((bb.get(0) >> 6) &0x1) == 1;
    }

    public boolean hasRSV2() {
      return ((bb.get(0) >> 5) &0x1) == 1;
    }

    public boolean hasRSV3() {
      return ((bb.get(0) >> 4) &0x1) == 1;
    }

    public int getOpCode() {
      return bb.get(0) & 0xf;
    }

    public boolean hasMask() {
      return (bb.get(1) &0xff) >> 7 == 1;
    }

    public long getPayloadDataLength() {
      byte sl = getSmallLen(bb);
      if(sl < WS_SHORT_SIZE) {
        return sl;
      } else if(sl == WS_SHORT_SIZE) {
        return bb.getShort(2) & 0xffff;
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
        ba[0] = bb.get(start);
        ba[1] = bb.get(start+1);
        ba[2] = bb.get(start+2);
        ba[3] = bb.get(start+3);
        return ba;
      }
      return ba;
    }

    public ByteBuffer unmaskPayload(final ByteBuffer nbb) {
      if(!hasMask()) {
        return nbb;
      } else {
        return unmaskData(nbb, getMaskValue());
      }
    }
  }
}
