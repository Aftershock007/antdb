package antdb.query;

// The Value interface represents different types of values that can be stored in the database.
// It defines a sealed interface to restrict implementations to specific types of values.
public sealed interface Value {

  // Represents a null value in the database.
  record NullValue() implements Value {}

  // Represents an integer value in the database.
  record IntValue(long value) implements Value {}

  // Represents a binary large object (BLOB) value in the database.
  record BlobValue(byte[] blob) implements Value {}

  // Represents a string value in the database.
  record StringValue(String data) implements Value {}

  // Retrieves the string representation of this value if it is a StringValue.
  default String getString() {
    return ((StringValue) this).data; // Casts to StringValue and returns its data.
  }

  // Retrieves the integer representation of this value if it is an IntValue.
  default long getInt() {
    return ((IntValue) this).value; // Casts to IntValue and returns its value.
  }

  // Compares this value with another Value for ordering.
  default int compareTo(Value other) {
    return switch (this) {
      case IntValue i -> (int) (i.value - other.getInt()); // Compare integer values.
      case StringValue s -> s.data.compareTo(other.getString()); // Compare string values lexicographically.
      case NullValue ignored -> Integer.MIN_VALUE; // Treat null as less than any other value.
      default -> throw new IllegalArgumentException("can't compare %s against value: %s".formatted(this, other)); // Throw error for unsupported comparisons.
    };
  }

  // Returns a string representation of this value for display purposes.
  default String display() {
    return switch (this) {
      case IntValue(long x) -> "%d".formatted(x); // Format integer values as strings.
      case StringValue(String x) -> "%s".formatted(x); // Format string values as strings.
      case NullValue() -> "NULL"; // Represent null values as "NULL".
      case BlobValue(byte[] ignored) -> "[blob]"; // Represent blob values with a placeholder text.
    };
  }
}