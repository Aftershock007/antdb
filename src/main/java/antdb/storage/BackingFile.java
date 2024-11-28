package antdb.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

public record BackingFile(SeekableByteChannel file) implements AutoCloseable {
  public int read(ByteBuffer buf) {
    try {
      return file.read(buf);
    } catch (IOException e) {
      throw new StorageException("failed to read from file", e);
    }
  }

  public BackingFile seek(long pos) {
    try {
      return new BackingFile(file.position(pos));
    } catch (IOException e) {
      throw new StorageException(
          String.format("failed to read offset %d in page", pos), e);
    }
  }

  public void close() {
    try {
      file.close();
    } catch (IOException e) {
      throw new StorageException("failed to close backing file", e);
    }
  }
}
