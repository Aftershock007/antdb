package antdb.storage;

import antdb.query.Value;
import antdb.sql.AST;
import antdb.sql.Parser;
import antdb.sql.SQLException;
import antdb.sql.Scanner;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class Index {
  private final StorageEngine storage;
  private final String name;
  private final Table table;
  private final Page.IndexPage root;
  private final AST.CreateIndexStatement definition;

  public Index(StorageEngine storage, String name, Table table,
               Page.IndexPage root, String schema) {
    this.storage = storage;
    this.name = name;
    this.table = table;
    this.root = root;
    this.definition = new Parser(new Scanner(schema)).createIndex();
  }

  public record Key(List<Value> indexKey, long rowId) {}

  // TODO: move this into IndexedPage and make its generic type Comparable
  private static boolean contains(Pointer<Key> page, Value value) {
    // TODO: handle different collating functions
    Optional<Value> left =
        page.left().get().map(key -> key.indexKey.getFirst());
    Optional<Value> right =
        page.right().get().map(key -> key.indexKey.getFirst());
    return (left.isEmpty() || left.get().compareTo(value) <= 0) &&
           (right.isEmpty() || right.get().compareTo(value) >= 0);
  }

  void collect(Page.IndexPage page, HashSet<Long> rows, Value filter) {
    switch (page) {
      case Page.IndexInteriorPage interior -> interior
          .records()
          .filter(childPtr -> contains(childPtr, filter))
          .forEach(childPtr -> {
            childPtr.left().get()
                    .filter(k -> k.indexKey.getFirst().equals(filter))
                    .ifPresent(k -> rows.add(k.rowId));
            childPtr.right().get()
                    .filter(k -> k.indexKey.getFirst().equals(filter))
                    .ifPresent(k -> rows.add(k.rowId));
            var child = storage.getPage(childPtr.pageNumber()).asIndexPage();
            collect(child, rows, filter);
          });

      case Page.IndexLeafPage leaf -> leaf
          .records()
          .filter(key -> key.indexKey.getFirst().equals(filter))
          .forEach(key -> rows.add(key.rowId));
    }
  }

  public String name() {return name;}

  public Table table() {return table;}

  // TODO: return a string
  public AST.CreateIndexStatement definition() {return definition;}

  public List<Long> findMatchingRecordIds(String column, Value value) {
    if (!definition.column().equals(column)) {
      throw new SQLException(
          "index %s does not cover column %s".formatted(name, column));
    }
    var rows = new HashSet<Long>();
    collect(root, rows, value);
    return rows.stream().toList();
  }
}
