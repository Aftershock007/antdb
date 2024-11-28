package antdb.query;

public sealed interface Value {
  record NullValue() implements Value {}
  record IntValue(long value) implements Value {}
  record BlobValue(byte[] blob) implements Value {}
  record StringValue(String data) implements Value {}

  default String getString() {return ((StringValue) this).data;}

  default long getInt() {return ((IntValue) this).value;}

  default int compareTo(Value other) {
    return switch (this) {
      case IntValue i -> (int) (i.value - other.getInt());
      case StringValue s -> s.data.compareTo(other.getString());
      case NullValue ignored -> Integer.MIN_VALUE;
      default -> throw new IllegalArgumentException(
          "can't compare %s against value: %s".formatted(this, other)
      );
    };
  }

  default String display() {
    return switch (this) {
      case IntValue(var x) -> "%d".formatted(x);
      case StringValue(var x) -> "%s".formatted(x);
      case NullValue() -> "NULL";
      case BlobValue(var ignored) -> "[blob]";
    };
  }
}
