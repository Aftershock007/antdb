package antdb.storage;

import org.junit.jupiter.api.Test;
import antdb.query.Value;
import antdb.storage.Pointer.Bounded;
import antdb.storage.Pointer.Unbounded;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PageTest {
  // create the page at an offset to catch more bugs
  private static final int PAGE_SIZE = 4096;
  private static final int PAGE_OFFSET = 17;

  private static ByteBuffer testPage(
      Page.Type type,
      int[] interiorPageRightMostPointer,
      // int array to allow passing an array literal without suicide
      int[][] cells
  ) {
    final var buf = ByteBuffer
        .allocate(PAGE_SIZE + PAGE_OFFSET)
        .order(ByteOrder.BIG_ENDIAN);

    // fill the header
    buf.position(PAGE_OFFSET).put(type.value);
    buf.position(PAGE_OFFSET + 3).putShort((short) cells.length);
    buf.position(PAGE_OFFSET + 8).put(toBytes(interiorPageRightMostPointer));

    // fill the pointer and content arrays
    final int afterHeader = PAGE_OFFSET + Page.headerSize(type);
    int pointerOffset = afterHeader;
    int cellOffset = afterHeader + 2 * cells.length;
    for (var cell : cells) {
      buf.position(pointerOffset).putShort((short) cellOffset);
      buf.position(cellOffset).put(toBytes(cell));
      pointerOffset += 2;
      cellOffset += cell.length;
    }

    return buf;
  }

  static byte[] toBytes(int[] ints) {
    byte[] bytes = new byte[ints.length];
    IntStream.range(0, ints.length).forEach(i -> bytes[i] = (byte) ints[i]);
    return bytes;
  }

  private static int[] concat(int[] left, int[] right) {
    return IntStream.concat(Arrays.stream(left), Arrays.stream(right))
                    .toArray();
  }

  private static int[] concat(int[]... arrays) {
    return Arrays.stream(arrays).reduce(PageTest::concat)
                 .orElseGet(() -> new int[0]);
  }

  @Test
  public void testTableInteriorPage() {
    int[][] cells = new int[][]{
        // <page number, integer key>
        concat(new int[]{0, 0, 0, 1}, new int[]{2}),
        concat(new int[]{0, 0, 0, 2}, new int[]{4}),
        concat(new int[]{0, 0, 0, 3}, new int[]{6}),
        concat(new int[]{0, 0, 0, 4}, new int[]{8}),
    };
    var buf = testPage(Page.Type.TABLE_INTERIOR, new int[]{0, 0, 0, 5}, cells);

    var page = Page.from(buf, PAGE_OFFSET, StandardCharsets.UTF_8);

    page.asTablePage();
    assertThrows(StorageException.class, page::asIndexPage);
    assertEquals(4, page.getNumCells());
    assertEquals(5, page.numRecords());
    assertEquals(StandardCharsets.UTF_8, page.getCharset());
    assertEquals(12, page.headerSize());
    assertEquals(
        List.of(
            new Pointer<>(new Unbounded<>(), new Bounded<>(2L), 1),
            new Pointer<>(new Bounded<>(2L), new Bounded<>(4L), 2),
            new Pointer<>(new Bounded<>(4L), new Bounded<>(6L), 3),
            new Pointer<>(new Bounded<>(6L), new Bounded<>(8L), 4),
            new Pointer<>(new Bounded<>(8L), new Unbounded<>(), 5)
        ),
        page.records().toList()
    );
  }

  @Test
  public void testTableLeafPage() {
    int[][] cells = new int[][]{
        // <payload size, integer key, row data>
        concat(new int[]{3}, new int[]{1}, new int[]{2, 1, -17}),
        concat(new int[]{3}, new int[]{2}, new int[]{2, 1, 0}),
        concat(new int[]{3}, new int[]{3}, new int[]{2, 1, 4}),
    };
    var buf = testPage(Page.Type.TABLE_LEAF, new int[]{}, cells);

    var page = Page.from(buf, PAGE_OFFSET, StandardCharsets.UTF_8);

    page.asTablePage();
    assertThrows(StorageException.class, page::asIndexPage);
    assertEquals(3, page.getNumCells());
    assertEquals(3, page.numRecords());
    assertEquals(StandardCharsets.UTF_8, page.getCharset());
    assertEquals(8, page.headerSize());
    assertEquals(
        List.of(
            new Page.Row(1, new Record(List.of(new Value.IntValue(-17)))),
            new Page.Row(2, new Record(List.of(new Value.IntValue(0)))),
            new Page.Row(3, new Record(List.of(new Value.IntValue(4))))
        ),
        page.records().toList()
    );
  }

  @Test
  public void testIndexInteriorPage() {
    int[][] cells = new int[][]{
        // cell = <page number, payload size, payload>
        // payload = <header size, column descriptors, values>
        concat(new int[]{0, 0, 0, 1}, new int[]{5},
               new int[]{3, 15, 1, 'a', 2}),
        concat(new int[]{0, 0, 0, 2}, new int[]{5},
               new int[]{3, 15, 1, 'b', 4}),
    };
    var buf = testPage(Page.Type.INDEX_INTERIOR, new int[]{0, 0, 0, 3}, cells);

    var page = Page.from(buf, PAGE_OFFSET, StandardCharsets.UTF_8);

    page.asIndexPage();
    assertThrows(StorageException.class, page::asTablePage);
    assertEquals(2, page.getNumCells());
    assertEquals(3, page.numRecords());
    assertEquals(StandardCharsets.UTF_8, page.getCharset());
    assertEquals(12, page.headerSize());
    assertEquals(
        List.of(
            new Pointer<>(new Unbounded<>(),
                          new Bounded<>(indexKey("a", 2)), 1),
            new Pointer<>(new Bounded<>(indexKey("a", 2)),
                          new Bounded<>(indexKey("b", 4)), 2),
            new Pointer<>(new Bounded<>(indexKey("b", 4)),
                          new Unbounded<>(), 3)
        ),
        page.records().toList()
    );
  }

  private static Index.Key indexKey(String value, long rowId) {
    return new Index.Key(List.of(new Value.StringValue(value)), rowId);
  }

  @Test
  public void testIndexLeafPage() {
    // TODO
  }

  @Test
  public void testInvalidPage() {
    // TODO
  }
}
