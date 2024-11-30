package antdb.storage;

import antdb.query.Value;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

// The Record class represents a collection of values that correspond to a single row in a database table.
// It provides methods to parse these values from a byte array, allowing for efficient serialization and deserialization.
public record Record(List<Value> values) {
  // A private record to hold the size and value of each parsed entry.
  private record SizedValue(int size, Value value) {}

  // Parses a byte array payload into a Record object using the specified character set.
  public static Record parse(byte[] payload, Charset charset) throws StorageException {
    ArrayList<Value> values = new ArrayList<>(); // List to hold the parsed values.
    // Wrap the byte array in a ByteBuffer with big-endian order.
    ByteBuffer buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);

    // Read the header size which indicates how many bytes are used for the header.
    VarInt headerSize = VarInt.parseFrom(buf.position(0));
    int headerOffset = headerSize.size(); // Size of the header in bytes.
    int contentOffset = (int) headerSize.value(); // Offset where the actual data starts.

    // Loop through the header to read each serialized value.
    while (headerOffset < headerSize.value()) {
      // Read the type of the serialized value.
      VarInt serialType = VarInt.parseFrom(buf.position(headerOffset));
      headerOffset += serialType.size(); // Move past the size of the serial type.
      int n = (int) serialType.value(); // Get the actual serial type value.

      // Determine how to parse the value based on its type.
      SizedValue sizedValue = switch (n) {
        case 0 -> new SizedValue(0, new Value.NullValue()); // Null value
        case 1 -> new SizedValue(1, new Value.IntValue(buf.position(contentOffset).get())); // 1-byte integer
        case 2 -> new SizedValue(2, new Value.IntValue(buf.position(contentOffset).getShort())); // 2-byte integer
        case 3 -> new SizedValue(3, new Value.IntValue(
                (Byte.toUnsignedInt(buf.position(contentOffset).get()) << 16) |
                        (Byte.toUnsignedInt(buf.position(contentOffset + 1).get()) << 8) |
                        (Byte.toUnsignedInt(buf.position(contentOffset + 2).get()))
        )); // 3-byte integer
        case 4 -> new SizedValue(4, new Value.IntValue(buf.position(contentOffset).getInt())); // 4-byte integer
        case 8 -> new SizedValue(0, new Value.IntValue(0)); // Special case for zero-sized integer
        case 9 -> new SizedValue(0, new Value.IntValue(1)); // Special case for one-sized integer
        default -> {
          if (n < 12) {
            // Throw an exception for invalid serial types that are too small.
            throw new StorageException("invalid serial type: %d".formatted(n));
          } else if (n % 2 == 0) {
            byte[] blob = new byte[(n - 12) / 2]; // Allocate space for a BLOB based on size.
            buf.position(contentOffset).get(blob); // Read BLOB data into allocated array.
            yield new SizedValue((n - 12) / 2, new Value.BlobValue(blob)); // Return BLOB value.
          } else {
            byte[] data = new byte[(n - 13) / 2]; // Allocate space for a STRING based on size.
            buf.position(contentOffset).get(data); // Read string data into allocated array.
            yield new SizedValue((n - 13) / 2, new Value.StringValue(new String(data, charset))); // Return STRING value created from byte array using specified charset.
          }
        }
      };
      values.add(sizedValue.value()); // Add parsed value to the list of values.
      contentOffset += sizedValue.size(); // Move content offset forward by the size of the parsed value.
    }
    return new Record(values); // Return a Record object containing all parsed values.
  }
}