package antdb.storage;

import antdb.query.Value;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

// TODO: unit tests
public record Record(List<Value> values) {

  private record SizedValue(int size, Value value) {}

  public static Record parse(byte[] payload, Charset charset)
  throws StorageException {
    var values = new ArrayList<Value>();
    ByteBuffer buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
    var headerSize = VarInt.parseFrom(buf.position(0));
    int headerOffset = headerSize.size();
    int contentOffset = (int) headerSize.value();
    while (headerOffset < headerSize.value()) {
      var serialType = VarInt.parseFrom(buf.position(headerOffset));
      headerOffset += serialType.size();
      int n = (int) serialType.value();
      var sizedValue = switch (n) {
        case 0 -> new SizedValue(0, new Value.NullValue());
        case 1 -> new SizedValue(1, new Value.IntValue(
            buf.position(contentOffset).get()));
        case 2 -> new SizedValue(2, new Value.IntValue(
            buf.position(contentOffset).getShort()));
        case 3 -> new SizedValue(3, new Value.IntValue(
            (Byte.toUnsignedInt(buf.position(contentOffset).get()) << 16) |
            (Byte.toUnsignedInt(buf.position(contentOffset + 1).get()) << 8) |
            (Byte.toUnsignedInt(buf.position(contentOffset + 2).get())))
        );
        case 4 -> new SizedValue(4, new Value.IntValue(
            buf.position(contentOffset).getInt()));
        case 8 -> new SizedValue(0, new Value.IntValue(0));
        case 9 -> new SizedValue(0, new Value.IntValue(1));
        default -> {
          if (n < 12) {
            throw new StorageException(
                "invalid serial type: %d".formatted(n));
          } else if (n % 2 == 0) {
            var blob = new byte[(n - 12) / 2];
            buf.position(contentOffset).get(blob);
            yield new SizedValue((n - 12) / 2,
                                 new Value.BlobValue(blob));
          } else {
            var data = new byte[(n - 13) / 2];
            buf.position(contentOffset).get(data);
            yield new SizedValue((n - 13) / 2, new Value.StringValue(
                new String(data, charset)));
          }
        }
      };
      values.add(sizedValue.value());
      contentOffset += sizedValue.size();
    }
    return new Record(values);
  }
}
