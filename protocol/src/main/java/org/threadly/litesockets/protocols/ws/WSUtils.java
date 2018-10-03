package org.threadly.litesockets.protocols.ws;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

import org.threadly.litesockets.buffers.MergedByteBuffers;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPHeaders;

public class WSUtils {
  
  //Keep private so it wont be modified.
  private static final byte[] MAGIC_UUID_BA = WSConstants.MAGIC_UUID.getBytes();

  public static String makeSecretKey() {
    return makeSecretKey(WSConstants.DEFAULT_SECRET_KEY_SIZE);
  }
  
  public static String makeSecretKey(final int size) {
    byte[] ba = new byte[size];
    ThreadLocalRandom.current().nextBytes(ba);
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
      md = MessageDigest.getInstance(WSConstants.DEFAULT_SECRET_HASH_ALGO);
      md.update(str.getBytes());
      md.update(MAGIC_UUID_BA);
      return Base64.getEncoder().encodeToString(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("HUGE problem we dont support the SHA1 hash cant to websockets!!!!!", e);
    }
  }
  
  public static boolean validateKeyResponse(final String origKey, final HTTPHeaders headers) {
    String key = headers.getHeader(HTTPConstants.HTTP_KEY_WEBSOCKET_ACCEPT);
    return validateKeyResponse(origKey, key);
  }
  
  public static boolean validateKeyResponse(final String orig, final String response) {
    String correctResponse =  makeKeyResponse(orig);
    return response.equals(correctResponse);
  }
  
  /**
   * This will mask or unmask data against provided mask.
   * 
   * @param nbb the {@link ByteBuffer} to apply the mask to.
   * @param mask the mask to apply to the ByteBuffer.
   * @return {@link ByteBuffer} with the provided mask applyed to it.
   */
  public static ByteBuffer maskData(final ByteBuffer nbb, final int mask) {
    if(mask == 0) {
      return nbb;
    } else {
      byte[] maskArray = ByteBuffer.allocate(WSConstants.MASK_SIZE).putInt(mask).array();
      ByteBuffer rbb = ByteBuffer.allocate(nbb.remaining());
      while(nbb.remaining()>=WSConstants.MASK_SIZE) {
        rbb.putInt(nbb.getInt()^mask);
      }
      for(int i=0; nbb.remaining() > 0; i++) {
        rbb.put((byte)(nbb.get()^maskArray[i%WSConstants.MASK_SIZE]));
      }
      rbb.flip();
      return rbb;
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
    return getFrameLength(nmbb.pullBuffer(Math.min(nmbb.remaining(), WSConstants.MAX_WS_FRAME_SIZE)));
  }
  
  /**
   * Gives the total length of the next Frame in the provided {@link ByteBuffer}.
   * This does not modify the {@link ByteBuffer}.
   * 
   * @param bb the {@link ByteBuffer} to find the frame length on.
   * @return the size of the frame in this {@link ByteBuffer}.
   */
  public static int getFrameLength(final ByteBuffer bb) {
    if(bb.remaining() < WSConstants.MIN_WS_FRAME_SIZE) {
      return -1;
    }

    int size = WSConstants.MIN_WS_FRAME_SIZE + getLengthSize(bb);
    if(hasMask(bb)) {
      size += WSConstants.MASK_SIZE;
    } 
    return size;
  }
  
  static byte getSmallLen(final ByteBuffer bb) {
    return (byte)(bb.get(1) & WSConstants.WS_SMALL_LENGTH_MASK);            
  }

  static int getLengthSize(final ByteBuffer bb) {
    final byte sl = getSmallLen(bb);
    if(sl == WSConstants.WS_SHORT_SIZE) {
      return WSConstants.WS_SHORT_LENGTH;
    } else if(sl == WSConstants.WS_LONG_SIZE) {
      return WSConstants.WS_LONG_LENGTH;
    }
    return 0;
  }

  private static boolean hasMask(final ByteBuffer bb) {
    return (bb.get(1) & WSConstants.UNSIGN_BYTE_MASK) >> WSConstants.STATIC_SEVEN == 1;
  }
}
