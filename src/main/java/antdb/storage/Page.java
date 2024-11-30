package antdb.storage;

import antdb.query.Value;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

// The Page class serves as an abstract base class for different types of pages in the database's storage system.
// It represents both leaf and interior pages used in B-tree structures for indexing and table data storage.
public sealed abstract class Page<T> permits Page.LeafPage, Page.InteriorPage {

  // Enumeration representing the type of page (leaf or interior) in the B-tree structure.
  public enum Type {
    TABLE_LEAF(0x0d), // Leaf page for table data
    TABLE_INTERIOR(0x05), // Interior page for table data
    INDEX_LEAF(0x0a), // Leaf page for index data
    INDEX_INTERIOR(0x02); // Interior page for index data

    public final byte value; // Byte representation of the page type

    Type(int value) {
      this.value = (byte) value; // Store the byte value for the page type
    }
  }

  // Returns the header size based on the type of page.
  public static short headerSize(Type type) {
    return switch (type) {
      case TABLE_INTERIOR, INDEX_INTERIOR -> 12; // Header size for interior pages
      case TABLE_LEAF, INDEX_LEAF -> 8; // Header size for leaf pages
    };
  }

  private final ByteBuffer buf; // Buffer holding the raw data for this page
  private final int base; // Base position in the buffer where this page's data starts
  private final short numCells; // Number of cells (records) in this page
  private final Charset charset; // Character set used for encoding text data

  // Constructor initializes a Page with a ByteBuffer, base position, and character set.
  protected Page(ByteBuffer buf, int base, Charset charset) {
    this.base = base; // Set the base position
    this.buf = buf; // Set the buffer containing page data
    this.numCells = buf.position(base + 3).getShort(); // Read number of cells from buffer at a specific offset
    this.charset = charset; // Set the character set for text encoding
  }

  protected short getNumCells() {
    return numCells; // Returns the number of cells in this page
  }

  protected Charset getCharset() {
    return charset; // Returns the character set used for this page
  }

  // Calculates and returns the offset of a specific cell based on its index.
  protected short cellOffset(int index) {
    return buf.position(base + headerSize() + index * 2).getShort();
    // Computes offset based on base position, header size, and cell index.
  }

  // Generates a stream of records contained within this page.
  public Stream<T> records() {
    final AtomicInteger n = new AtomicInteger(0); // Atomic counter to track record indices
    // Generates a stream of records until reaching the total number of records.
    return Stream.generate(() -> parseRecord(n.getAndIncrement(), buf)).limit(numRecords());
  }

  // Abstract method that must be implemented by subclasses to return the number of records in this page.
  public abstract int numRecords();

  // Abstract method to get header size specific to page type
  protected abstract int headerSize();

  // Abstract method to parse a record from the buffer based on its index.
  protected abstract T parseRecord(int index, ByteBuffer buf);

  // Factory method to create a Page instance from a ByteBuffer based on its type.
  static Page<?> from(ByteBuffer buf, int base, Charset charset) {
    byte type = buf.position(base).get();
    return switch (type) {
      case 0x02 -> new IndexInteriorPage(buf, base, charset);
      case 0x05 -> new TableInteriorPage(buf, base, charset);
      case 0x0a -> new IndexLeafPage(buf, base, charset);
      case 0x0d -> new TableLeafPage(buf, base, charset);
      // Throws an exception for unsupported or unknown page types.
      default -> throw new StorageException("invalid page type: %x".formatted(type));
    };
  }

  // =======================
  // Leaf and interior pages
  // =======================

  static sealed abstract class LeafPage<T> extends Page<T> permits TableLeafPage, IndexLeafPage {
    private LeafPage(ByteBuffer buf, int base, Charset charset) {
      super(buf, base, charset); // Call parent constructor to initialize buffer and positions.
    }

    @Override
    protected int headerSize() {
      return 8; // Return header size specific to leaf pages.
    }

    @Override
    public int numRecords() {
      return getNumCells(); // For leaf pages, number of records equals number of cells.
    }
  }

  static sealed abstract class InteriorPage<T> extends Page<Pointer<T>> permits TableInteriorPage, IndexInteriorPage {
    private final int rightPage; // The right child page number

    private InteriorPage(ByteBuffer buf, int base, Charset charset) {
      super(buf, base, charset);
      // Read right child page number from buffer at a specific offset.
      rightPage = buf.position(base + 8).getInt();
    }

    @Override
    protected int headerSize() {
      return 12; // Return header size specific to interior pages.
    }

    @Override
    public int numRecords() {
      // For interior pages, number of records is number of cells plus one (for right child).
      return getNumCells() + 1;
    }

    protected record Cell<T>(int cellId, T payload) {}

    protected abstract Cell<T> parseCell(int index, ByteBuffer buf);

    @Override
    protected Pointer<T> parseRecord(int index, ByteBuffer buf) {
      if (index == 0) {
        Cell<T> cell = parseCell(index, buf);
        // Handle first record case with special pointer structure.
        return new Pointer<>(new Pointer.Unbounded<>(), new Pointer.Bounded<>(cell.payload), cell.cellId);
      } else if (index == getNumCells()) {
        Cell<T> cell = parseCell(index - 1, buf);
        // Handle last record case with right child pointer.
        return new Pointer<>(new Pointer.Bounded<>(cell.payload), new Pointer.Unbounded<>(), rightPage);
      } else {
        Cell<T> prev = parseCell(index - 1, buf);
        Cell<T> cur = parseCell(index, buf);
        // Handle middle records with both left and right pointers.
        return new Pointer<>(new Pointer.Bounded<>(prev.payload), new Pointer.Bounded<>(cur.payload), cur.cellId);
      }
    }
  }

  // =====================
  // Table and index pages
  // =====================

  public sealed interface TablePage permits TableLeafPage, TableInteriorPage {}

  public TablePage asTablePage() {
    if (this instanceof TablePage page) return page;
    // Ensure current instance is a table page before returning it.
    throw new StorageException("wanted table page, got %s".formatted(this.getClass()));
  }

  public sealed interface IndexPage permits IndexLeafPage, IndexInteriorPage {}

  public IndexPage asIndexPage() {
    if (this instanceof IndexPage page) return page;
    // Ensure current instance is an index page before returning it.
    throw new StorageException("wanted index page, got %s".formatted(this.getClass()));
  }

  // ===================
  // Concrete page types
  // ===================

  record Row(long rowId, Record values) {}

  static final class TableLeafPage extends LeafPage<Row> implements TablePage {
    TableLeafPage(ByteBuffer buf, int base, Charset charset) {
      // Call parent constructor to initialize buffer and positions for leaf pages.
      super(buf, base, charset);
    }

    @Override
    protected Row parseRecord(int index, ByteBuffer buf) {
      int offset = cellOffset(index);
      VarInt payloadSize = VarInt.parseFrom(buf.position(offset));
      offset += payloadSize.size();
      VarInt rowId = VarInt.parseFrom(buf.position(offset));
      offset += rowId.size();
      byte[] payload = new byte[(int) payloadSize.value()];
      buf.position(offset).get(payload);
      // Parse row ID and values from buffer and create a Row object.
      return new Row(rowId.value(), Record.parse(payload, getCharset()));
    }
  }

  static final class TableInteriorPage extends InteriorPage<Long> implements TablePage {
    private TableInteriorPage(ByteBuffer buf, int base, Charset charset) {
      // Call parent constructor to initialize buffer and positions for interior pages.
      super(buf, base, charset);
    }

    @Override
    protected Cell<Long> parseCell(int index, ByteBuffer buf) {
      if (index >= getNumCells()) throw new AssertionError("index < numCells");
      int offset = cellOffset(index);
      int pageNumber = buf.position(offset).getInt();
      VarInt rowId = VarInt.parseFrom(buf.position(offset + 4));
      // Parse cell information including child page number and row ID.
      return new Cell<>(pageNumber, rowId.value());
    }
  }

  static final class IndexLeafPage extends LeafPage<Index.Key> implements IndexPage {
    IndexLeafPage(ByteBuffer buf, int base, Charset charset) {
      // Call parent constructor to initialize buffer and positions for index leaf pages.
      super(buf, base, charset);
    }

    @Override
    protected Index.Key parseRecord(int index, ByteBuffer buf) {
      if (index >= getNumCells()) {
        throw new AssertionError("index < numCells");
      }
      int offset = cellOffset(index);
      var payloadSize = VarInt.parseFrom(buf.position(offset));
      offset += payloadSize.size();
      byte[] payload = new byte[(int) payloadSize.value()];
      buf.position(offset).get(payload);
      Record record = Record.parse(payload,getCharset());
      Value rowId = record.values().removeLast();
      // Parse key information from buffer and create an Index.Key object representing indexed values and their associated row ID.
      return new Index.Key(record.values(), rowId.getInt());
    }
  }

  static final class IndexInteriorPage extends InteriorPage<Index.Key> implements IndexPage {
    private IndexInteriorPage(ByteBuffer buf,int base ,Charset charset) {
      // Call parent constructor to initialize buffer and positions for index interior pages.
      super(buf ,base ,charset);
    }

    @Override
    protected Cell<Index.Key> parseCell(int index ,ByteBuffer buf){
      if(index >= getNumCells()) {
        throw new AssertionError("index < numCells");
      }
      // Ensure that the given index is within bounds.
      int offset = cellOffset(index); // Get cell offset based on provided index.
      int pageNumber = buf.position(offset).getInt(); // Read child page number from buffer.
      offset += 4; // Move past the integer read.
      VarInt payloadSize = VarInt.parseFrom(buf.position(offset)); // Parse variable-length integer size.
      offset += payloadSize.size(); // Move past the size read.
      byte[] payload = new byte[(int) payloadSize.value()]; // Allocate space for payload based on parsed size.
      buf.position(offset).get(payload); // Read actual payload data into allocated array.
      Record record = Record.parse(payload,getCharset()); // Parse record from payload using defined character set.
      Value rowId = record.values().removeLast(); // Extract row ID from parsed values.
      // Return parsed cell containing child pointer and key information.
      return new Cell<>(pageNumber,new Index.Key(record.values(),rowId.getInt()));
    }
  }
}