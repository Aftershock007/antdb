package antdb.storage;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public sealed abstract class Page<T> permits Page.LeafPage, Page.InteriorPage {
  public enum Type {
    TABLE_LEAF(0x0d),
    TABLE_INTERIOR(0x05),
    INDEX_LEAF(0x0a),
    INDEX_INTERIOR(0x02);

    public final byte value;

    Type(int value) {
      this.value = (byte) value;
    }
  }

  public static short headerSize(Type type) {
    return switch (type) {
      case TABLE_INTERIOR, INDEX_INTERIOR -> 12;
      case TABLE_LEAF, INDEX_LEAF -> 8;
    };
  }

  private final ByteBuffer buf;
  private final int base;
  private final short numCells;
  private final Charset charset;

  protected Page(ByteBuffer buf, int base, Charset charset) {
    this.base = base;
    this.buf = buf;
    this.numCells = buf.position(base + 3).getShort();
    this.charset = charset;
  }

  protected short getNumCells() {
    return numCells;
  }

  protected Charset getCharset() {
    return charset;
  }

  protected short cellOffset(int index) {
    return buf.position(base + headerSize() + index * 2).getShort();
  }

  public Stream<T> records() {
    final var n = new AtomicInteger(0);
    return Stream.generate(() -> parseRecord(n.getAndIncrement(), buf))
                 .limit(numRecords());
  }

  // All page types must implement:
  public abstract int numRecords();
  protected abstract int headerSize();
  protected abstract T parseRecord(int index, ByteBuffer buf);

  static Page<?> from(ByteBuffer buf, int base, Charset charset) {
    byte type = buf.position(base).get();
    return switch (type) {
      case 0x02 -> new IndexInteriorPage(buf, base, charset);
      case 0x05 -> new TableInteriorPage(buf, base, charset);
      case 0x0a -> new IndexLeafPage(buf, base, charset);
      case 0x0d -> new TableLeafPage(buf, base, charset);
      default ->
          throw new StorageException("invalid page type: %x".formatted(type));
    };
  }

  // =======================
  // Leaf and interior pages
  // =======================

  static sealed abstract class LeafPage<T>
      extends Page<T>
      permits TableLeafPage, IndexLeafPage {
    private LeafPage(ByteBuffer buf, int base, Charset charset) {
      super(buf, base, charset);
    }

    @Override
    protected int headerSize() {return 8;}

    @Override
    public int numRecords() {return getNumCells();}
  }

  static sealed abstract class InteriorPage<T>
      extends Page<Pointer<T>>
      permits TableInteriorPage, IndexInteriorPage {
    private final int rightPage;

    private InteriorPage(ByteBuffer buf, int base, Charset charset) {
      super(buf, base, charset);
      rightPage = buf.position(base + 8).getInt();
    }

    @Override
    protected int headerSize() {return 12;}

    @Override
    public int numRecords() {return getNumCells() + 1;}

    protected record Cell<T>(int cellId, T payload) {}

    protected abstract Cell<T> parseCell(int index, ByteBuffer buf);

    @Override
    protected Pointer<T> parseRecord(int index, ByteBuffer buf) {
      if (index == 0) {
        var cell = parseCell(index, buf);
        return new Pointer<>(new Pointer.Unbounded<>(),
                             new Pointer.Bounded<>(cell.payload), cell.cellId);
      } else if (index == getNumCells()) {
        var cell = parseCell(index - 1, buf);
        return new Pointer<>(new Pointer.Bounded<>(cell.payload),
                             new Pointer.Unbounded<>(), rightPage);
      } else {
        var prev = parseCell(index - 1, buf);
        var cur = parseCell(index, buf);
        return new Pointer<>(new Pointer.Bounded<>(prev.payload),
                             new Pointer.Bounded<>(cur.payload), cur.cellId);
      }
    }
  }

  // =====================
  // Table and index pages
  // =====================

  public sealed interface TablePage permits TableLeafPage, TableInteriorPage {}

  public TablePage asTablePage() {
    if (this instanceof TablePage page) return page;
    throw new StorageException(
        "wanted table page, got %s".formatted(this.getClass()));
  }

  public sealed interface IndexPage permits IndexLeafPage, IndexInteriorPage {}

  public IndexPage asIndexPage() {
    if (this instanceof IndexPage page) return page;
    throw new StorageException(
        "wanted index page, got %s".formatted(this.getClass()));
  }

  // ===================
  // Concrete page types
  // ===================

  record Row(long rowId, Record values) {}

  static final class TableLeafPage
      extends LeafPage<Row>
      implements TablePage {
    TableLeafPage(ByteBuffer buf, int base, Charset charset) {
      super(buf, base, charset);
    }

    @Override
    protected Row parseRecord(int index, ByteBuffer buf) {
      int offset = cellOffset(index);
      var payloadSize = VarInt.parseFrom(buf.position(offset));
      offset += payloadSize.size();
      var rowId = VarInt.parseFrom(buf.position(offset));
      offset += rowId.size();
      var payload = new byte[(int) payloadSize.value()];
      buf.position(offset).get(payload);
      // TODO: overflow pages
      return new Row(rowId.value(), Record.parse(payload, getCharset()));
    }
  }

  static final class TableInteriorPage
      extends InteriorPage<Long>
      implements TablePage {
    private TableInteriorPage(ByteBuffer buf, int base, Charset charset) {
      super(buf, base, charset);
    }

    @Override
    protected Cell<Long> parseCell(int index, ByteBuffer buf) {
      if (index >= getNumCells()) throw new AssertionError("index < numCells");
      int offset = cellOffset(index);
      int pageNumber = buf.position(offset).getInt();
      var rowId = VarInt.parseFrom(buf.position(offset + 4));
      return new Cell<>(pageNumber, rowId.value());
    }
  }

  static final class IndexLeafPage
      extends LeafPage<Index.Key>
      implements IndexPage {
    IndexLeafPage(ByteBuffer buf, int base, Charset charset) {
      super(buf, base, charset);
    }

    @Override
    protected Index.Key parseRecord(int index, ByteBuffer buf) {
      if (index >= getNumCells()) throw new AssertionError("index < numCells");
      int offset = cellOffset(index);
      var payloadSize = VarInt.parseFrom(buf.position(offset));
      offset += payloadSize.size();
      var payload = new byte[(int) payloadSize.value()];
      buf.position(offset).get(payload);
      var record = Record.parse(payload, getCharset());
      var rowId = record.values().removeLast();
      return new Index.Key(record.values(), rowId.getInt());
    }
  }

  static final class IndexInteriorPage
      extends InteriorPage<Index.Key>
      implements IndexPage {
    private IndexInteriorPage(ByteBuffer buf, int base, Charset charset) {
      super(buf, base, charset);
    }

    @Override
    protected Cell<Index.Key> parseCell(int index, ByteBuffer buf) {
      if (index >= getNumCells()) throw new AssertionError("index < numCells");
      int offset = cellOffset(index);
      int pageNumber = buf.position(offset).getInt();
      offset += 4;
      var payloadSize = VarInt.parseFrom(buf.position(offset));
      offset += payloadSize.size();
      var payload = new byte[(int) payloadSize.value()];
      buf.position(offset).get(payload);
      var record = Record.parse(payload, getCharset());
      var rowId = record.values().removeLast();
      return new Cell<>(pageNumber,
                        new Index.Key(record.values(), rowId.getInt()));
    }
  }
}
