package antdb.storage;

import java.nio.ByteBuffer;

// The VarInt class represents a variable-length integer used for efficient serialization and deserialization.
// It allows integers to be stored in a compact form, using fewer bytes for smaller values.
public record VarInt(long value, int size) {

  // Parses a variable-length integer from the given ByteBuffer.
  public static VarInt parseFrom(ByteBuffer buf) {
    long value = 0; // Initialize the variable to hold the parsed integer value.
    int size; // Variable to track the number of bytes used to encode the integer.
    // Read bytes from the buffer until we have a complete variable-length integer.
    for (size = 1; size <= 8; size++) {
      byte b = buf.get(); // Read the next byte from the buffer.
      int lower = Byte.toUnsignedInt(b) & 127; // Get the lower 7 bits of the byte (ignoring the sign bit).
      value <<= 7; // Shift the existing value left by 7 bits to make room for the new bits.
      value |= lower; // Add the lower bits to the value.
      // If the byte is non-negative, we've reached the end of the variable-length integer.
      if (b >= 0) break;
    }
    // If we read 9 bytes, it means we need to read one more byte for a larger integer.
    if (size == 9) {
      byte b = buf.get(); // Read one additional byte.
      value <<= 8; // Shift left by 8 bits to accommodate this new byte.
      value |= b; // Add this byte to our value.
    }

    return new VarInt(value, size); // Return a new VarInt instance with parsed value and its size.
  }
}