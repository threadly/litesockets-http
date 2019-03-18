package org.threadly.litesockets.protocols.websocket;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.concurrent.ThreadLocalRandom;

import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.buffers.SimpleMergedByteBuffers;

/**
 * WSFrame object.  This is allows you to easily get information about a WebSocket Frame data.
 * This object is immutable.
 * 
 */
public class WSFrame {
  private final ByteBuffer bb;
  private final int frameLength;

  protected WSFrame(final ByteBuffer bb) {
    frameLength = WSUtils.getFrameLength(bb);
    if(frameLength < 0 || bb.remaining() < frameLength) {
      throw new IllegalStateException("Not enough data to make a WebSocketFrame");
    }
    this.bb = bb;
  }
  
  /**
   * ByteBuffer for the raw frame.  This is just the frame, not the payload thats associated with it.
   * 
   * @return ByteBuffer of this frame.
   */
  public ByteBuffer getRawFrame() {
    return bb.duplicate();
  }

  /**
   * Is the finished bit set in the frame.
   * 
   * @return true if the frame is marked as finished, false if not.
   */
  public boolean isFinished() {
    return ((bb.get(0)&WSConstants.UNSIGN_BYTE_MASK) >> WSConstants.STATIC_SEVEN) == 1;
  }

  /**
   * Is the RSV1 bit set.
   * 
   * @return true if its set false if its not.
   */
  public boolean hasRSV1() {
    return ((bb.get(0) >> WSConstants.STATIC_SIX) &0x1) == 1;
  }

  /**
   * Is the RSV2 bit set.
   * 
   * @return true if its set false if its not.
   */
  public boolean hasRSV2() {
    return ((bb.get(0) >> WSConstants.STATIC_FIVE) &0x1) == 1;
  }

  /**
   * Is the RSV3 bit set.
   * 
   * @return true if its set false if its not.
   */
  public boolean hasRSV3() {
    return ((bb.get(0) >> WSConstants.STATIC_FOUR) &0x1) == 1;
  }

  /**
   * The opCode for this websocket frame.
   * 
   * @return The opCode for this websocket frame.
   */
  public int getOpCode() {
    return bb.get(0) & WSConstants.OPCODE_MASK;
  }

  /**
   * Is the mask bit set for this WSFrames payload.
   * 
   * @return true if the payload is masked, false if it is not.
   */
  public boolean hasMask() {
    return (bb.get(1) & WSConstants.UNSIGN_BYTE_MASK) >> WSConstants.STATIC_SEVEN == 1;
  }

  /**
   * Returns the size of the websocket frames payload.
   * 
   * @return size of the payload.
   */
  public long getPayloadDataLength() {
    byte sl = WSUtils.getSmallLen(bb);
    if(sl < WSConstants.WS_SHORT_SIZE) {
      return sl;
    } else if(sl == WSConstants.WS_SHORT_SIZE) {
      return bb.getShort(2) & WSConstants.UNSIGNED_SHORT_MASK;
    } else {
      return bb.getLong(2);
    }
  }

  /**
   * Gets the int used to mask this payload.
   * 
   * @return the int used to mask the payload.
   */
  public int getMaskValue() {
    if(hasMask()) {
      return bb.getInt(WSUtils.getFrameLength(bb)-WSConstants.MASK_SIZE);
    }
    return 0;
  }

  /**
   * Gets the mask as a byte array.
   * 
   * @return the make bytes as an array.
   */
  public byte[] getMaskArray() {
    byte[] ba = new byte[WSConstants.MASK_SIZE];
    if(hasMask()) {
      final int start = WSUtils.getFrameLength(bb)-WSConstants.MASK_SIZE;
      for(int i=0; i<WSConstants.MASK_SIZE; i++) {
        ba[i] = bb.get(start+i);  
      }
      return ba;
    }
    return ba;
  }

  /**
   * Takes a ByteBuffer if this WSFrames payload and unmasks it.
   * 
   * @param nbb the ByteBuffer payload of this WSFrame.
   * @return a ByteBuffer of the data unmasked.
   */
  public ByteBuffer unmaskPayload(final ByteBuffer nbb) {
    if(!hasMask()) {
      return nbb;
    } else {
      return WSUtils.maskData(nbb, getMaskValue());
    }
  }
  
  /**
   * Parses a WebSocket frame out of the provided {@link ByteBuffer}.
   * 
   * @param bb the ByteBuffer containing the WebSocketFrame.
   * @return a {@link WSFrame}.
   * @throws ParseException this is thrown if there is not enough data to make a {@link WSFrame}. 
   */
  public static WSFrame parseWSFrame(final ByteBuffer bb) throws ParseException {
    MergedByteBuffers mbb = new SimpleMergedByteBuffers(false, bb);
    int origSize = mbb.remaining();
    WSFrame wsf = parseWSFrame(mbb);
    bb.position(bb.position() + origSize - mbb.remaining());
    return wsf;
  }
  
  /**
   * Parses a WebSocket frame from the passed in {@link MergedByteBuffers}.  Only the data for the frame
   * will be parsed out of the ByteBuffer, the payload will remain.
   * 
   * @param mbb {@link MergedByteBuffers} containing the frame as the first bytes.
   * @return a {@link WSFrame} object to get info about the data in the {@link WSFrame}.
   * @throws ParseException this is thrown if there is not enough data to make a {@link WSFrame}.
   */
  public static WSFrame parseWSFrame(final MergedByteBuffers mbb) throws ParseException {
    final int size = WSUtils.getFrameLength(mbb);
    if(size > 0 && mbb.remaining() >= size) {
      ByteBuffer nbb = mbb.pullBuffer(size);
      return new WSFrame(nbb);
    } else {
      throw new ParseException("Not enough data to make a WebSocketFrame", 0);
    }
  }
  
  /**
   * Creates a {@link WSFrame} object with the provided parameters.
   * 
   * @param size the size of the payload in the WebSocket Frame.
   * @param opCode The opCode to put in this WebSocket.
   * @param mask true if a mask should be added to this frame, false if not.
   * @return a {@link WSFrame} object created with the provided params.
   */
  public static WSFrame makeWSFrame(final int size, byte opCode, final boolean mask) {
    return makeWSFrame(size, true, opCode, mask);
  }
  
  /**
   * Creates a {@link WSFrame} object with the provided parameters.
   * 
   * @param size the size of the payload in the WSFrame.
   * @param opCode The {@link WSOPCode} to use in this WSFrame.
   * @param mask true if a mask should be added to this frame, false if not.
   * @return a {@link WSFrame} object created with the provided params.
   */
  public static WSFrame makeWSFrame(final int size, WSOPCode opCode, final boolean mask) {
    return makeWSFrame(size, true, opCode.getValue(), mask);
  }
  
  /**
   * Creates a {@link WSFrame} object with the provided parameters.
   * 
   * @param size the size of the payload in the WSFrame.
   * @param isFinished true if we should mark this WSFrame as finished false if not.
   * @param opCode The opCode to put in this WebSocket.
   * @param mask true if a mask should be added to this frame, false if not.
   * @return a {@link WSFrame} object created with the provided params.
   */
  public static WSFrame makeWSFrame(final int size, boolean isFinished, byte opCode, final boolean mask) {

    ByteBuffer nbb;
    int maskExtra = mask ? WSConstants.MASK_SIZE : 0;
    byte bmask = mask ? (byte)1 : (byte)0;
    byte firstByte = opCode;
    if(isFinished) {
      firstByte = (byte)(firstByte | (1<<WSConstants.STATIC_SEVEN));
    }
    
    if(size < WSConstants.WS_SHORT_SIZE) {
      nbb = ByteBuffer.allocate(WSConstants.MIN_WS_FRAME_SIZE+maskExtra);
      nbb.put(firstByte);
      nbb.put((byte)(bmask<<WSConstants.STATIC_SEVEN | size));
    } else if (size <= WSConstants.UNSIGNED_SHORT_MASK) {
      nbb = ByteBuffer.allocate(WSConstants.MIN_WS_FRAME_SIZE+WSConstants.WS_SHORT_LENGTH+maskExtra);
      nbb.put(firstByte);
      nbb.put((byte)(bmask<<WSConstants.STATIC_SEVEN|WSConstants.WS_SHORT_SIZE));
      nbb.putShort((short)size);
    } else {
      nbb = ByteBuffer.allocate(WSConstants.MIN_WS_FRAME_SIZE+WSConstants.WS_LONG_LENGTH+maskExtra);
      nbb.put(firstByte);
      nbb.put((byte)(bmask<<WSConstants.STATIC_SEVEN|WSConstants.WS_LONG_SIZE));
      nbb.putLong(size);
    }

    if(mask) {
      nbb.putInt(ThreadLocalRandom.current().nextInt());
    }
    nbb.flip();
    return new WSFrame(nbb);
  }
}