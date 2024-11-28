package antdb.storage;

import java.nio.ByteBuffer;

public record VarInt(long value, int size) {
  public static VarInt parseFrom(ByteBuffer buf) {
    long value = 0;
    int size;
    for (size = 1; size <= 8; size++) {
      byte b = buf.get();
      int lower = Byte.toUnsignedInt(b) & 127;
      value <<= 7;
      value |= lower;
      if (b >= 0) break;
    }
    if (size == 9) {
      byte b = buf.get();
      value <<= 8;
      value |= b;
    }
    return new VarInt(value, size);
  }
}
