package antdb.storage;

import antdb.query.Value;
import antdb.sql.AST;
import antdb.sql.Parser;
import antdb.sql.Scanner;

import java.util.*;

public class Table {
  private final StorageEngine storage;
  private final String name;
  private final Page.TablePage root;
  private final AST.CreateTableStatement definition;

  Table(StorageEngine storage, String name, Page.TablePage root,
        String schema) {
    this.storage = storage;
    this.name = name;
    this.root = root;
    this.definition = new Parser(new Scanner(schema)).createTable();
  }

  private static boolean isIntegerPK(AST.ColumnDef col) {
    var mods = col.modifiers();
    return mods.contains("integer") && mods.contains("primary") &&
           mods.contains("key");
  }

  private Row parseRow(Page.Row row) {
    var record = new HashMap<String, Value>();
    for (int i = 0; i < definition.columns().size(); i++) {
      var col = definition.columns().get(i);
      var val = isIntegerPK(col)
          ? new Value.IntValue(row.rowId())
          : row.values().values().get(i);
      record.put(col.name(), val);
    }
    return new Row(row.rowId(), record);
  }

  private void collect(Page.TablePage page, List<Row> rows) {
    switch (page) {
      case Page.TableLeafPage leaf -> leaf
          .records()
          .map(this::parseRow)
          .forEach(rows::add);

      case Page.TableInteriorPage interior -> interior
          .records()
          .forEach(child -> collect(
              storage.getPage(child.pageNumber()).asTablePage(), rows));
    }
  }

  // TODO: move this into IndexedPage and make its generic type Comparable
  private static boolean contains(Pointer<Long> page, long rowId) {
    if (page.left() instanceof Pointer.Bounded<Long>(Long endpoint) &&
        rowId < endpoint) {
      return false;
    }
    return !(page.right() instanceof Pointer.Bounded<Long>(Long endpoint)) ||
           rowId <= endpoint;
  }

  private Optional<Row> lookup(Page.TablePage page, long rowId) {
    return switch (page) {
      case Page.TableInteriorPage interior -> interior
          .records()
          .filter(child -> contains(child, rowId))
          .findFirst()
          .flatMap(child -> lookup(
              storage.getPage(child.pageNumber()).asTablePage(), rowId));

      case Page.TableLeafPage leaf -> leaf
          .records()
          .filter(child -> child.rowId() == rowId)
          .map(this::parseRow)
          .findFirst();
    };
  }

  public record Row(long rowId, Map<String, Value> values) {
    public Value get(String column) {return values.get(column);}
  }

  public String name() {return name;}

  // TODO: stream
  public List<Row> rows() {
    var rows = new ArrayList<Row>();
    collect(root, rows);
    return rows;
  }

  public Optional<Row> get(long rowId) {
    return lookup(root, rowId);
  }
}
