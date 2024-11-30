package antdb.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

// Records are a special kind of class in Java that are designed to be simple data carriers. They automatically provide implementations of methods like equals(), hashCode(), and toString().
// AutoCloseable: This indicates that instances of BackingFile can be used in a try-with-resources statement, allowing for automatic resource management. When the instance is closed, it will release any resources associated with it.
// SeekableByteChannel: An interface that allows for reading and writing operations on a file with the ability to seek to different positions within it.
public record BackingFile(SeekableByteChannel file) implements AutoCloseable {
  // The read() method attempts to read data from the underlying file
  // (represented by the SeekableByteChannel) into the provided ByteBuffer.
  public int read(ByteBuffer buf) {
    try {
      return file.read(buf); // Reads bytes from the file into the buffer.
    } catch (IOException e) {
      throw new StorageException("failed to read from file", e); // Handle IOExceptions by wrapping them in a StorageException.
    }
  }

  // The seek() method moves the reading or writing position in the file
  // to a specific location defined by 'pos'. It returns a new instance
  // of BackingFile at that position.
  public BackingFile seek(long pos) {
    try {
      // Set the file's cursor (current reading/writing position) to the specified position (pos).
      // Creates a new BackingFile instance with this updated position.
      return new BackingFile(file.position(pos));
    } catch (IOException e) {
      throw new StorageException(String.format("failed to read offset %d in page", pos), e); // Handle IOExceptions for seeking errors.
    }
  }

  // The close() method is used to safely close the underlying file associated
  // with this BackingFile instance. This ensures that any resources are released properly.
  public void close() {
    try {
      file.close(); // Close the SeekableByteChannel, releasing associated resources.
    } catch (IOException e) {
      throw new StorageException("failed to close backing file", e); // Handle IOExceptions during closing.
    }
  }
}
