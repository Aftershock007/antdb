package antdb.storage;

import antdb.query.Value;
import antdb.sql.AST;
import antdb.sql.Parser;
import antdb.sql.SQLException;
import antdb.sql.Scanner;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

// The Index class represents a database index, which is used to optimize data retrieval operations.
// It allows for fast lookups of rows based on specific column values.
public class Index {
  private final StorageEngine storage; // Reference to the storage engine managing database operations.
  private final String name; // Name of the index.
  private final Table table; // The table to which this index belongs.
  private final Page.IndexPage root; // The root page of the index B-tree structure.
  private final AST.CreateIndexStatement definition; // Definition of the index schema.

  // Constructor initializes the Index object with necessary parameters.
  public Index(StorageEngine storage, String name, Table table, Page.IndexPage root, String schema) {
    this.storage = storage; // Store reference to the storage engine.
    this.name = name; // Store the name of the index.
    this.table = table; // Store reference to the associated table.
    this.root = root; // Store reference to the root page of the index.
    this.definition = new Parser(new Scanner(schema)).createIndex(); // Parse the schema definition into an AST representation for index creation.
  }

  // Represents a key in the index, consisting of a list of indexed values and a corresponding row ID.
  public record Key(List<Value> indexKey, long rowId) {}

  // Checks if a given value is contained within a specified pointer structure in the index.
  private static boolean contains(Pointer<Key> page, Value value) {
    // Get the first indexed value from the left pointer.
    Optional<Value> left = page.left().get().map(key -> key.indexKey.getFirst());
    // Get the first indexed value from the right pointer.
    Optional<Value> right = page.right().get().map(key -> key.indexKey.getFirst());
    // Check if value falls within the range defined by left and right keys (if they exist).
    return (left.isEmpty() || left.get().compareTo(value) <= 0) && (right.isEmpty() || right.get().compareTo(value) >= 0);
  }

  // Collects matching row IDs from the index based on a filter condition.
  void collect(Page.IndexPage page, HashSet<Long> rows, Value filter) {
    switch (page) {
      case Page.IndexInteriorPage interior -> interior
              .records()
              .filter(childPtr -> contains(childPtr, filter)) // Filter child pointers based on whether they contain the filter value.
              .forEach(childPtr -> {
                childPtr.left().get()
                        .filter(k -> k.indexKey.getFirst().equals(filter)) // Check if left key matches filter value.
                        .ifPresent(k -> rows.add(k.rowId)); // Add matching row ID to results.
                childPtr.right().get()
                        .filter(k -> k.indexKey.getFirst().equals(filter)) // Check if right key matches filter value.
                        .ifPresent(k -> rows.add(k.rowId)); // Add matching row ID to results.
                Page.IndexPage child = storage.getPage(childPtr.pageNumber()).asIndexPage();
                collect(child, rows, filter); // Recursively collect from child pages if necessary.
              });
      case Page.IndexLeafPage leaf -> leaf
              .records()
              .filter(key -> key.indexKey.getFirst().equals(filter))
              .forEach(key -> rows.add(key.rowId)); // Collect matching row IDs directly from leaf pages.
    }
  }

  public String name() {
    return name; // Returns the name of the index.
  }

  public Table table() {
    return table; // Returns the associated table for this index.
  }

  public AST.CreateIndexStatement definition() {
    return definition; // Returns the AST representation of the index definition.
  }

  // Finds matching record IDs based on a specified column and value using this index.
  public List<Long> findMatchingRecordIds(String column, Value value) {
    if (!definition.column().equals(column)) {
      // Throws an exception if this index does not cover the specified column.
      throw new SQLException("index %s does not cover column %s".formatted(name, column));
    }

    HashSet<Long> rows = new HashSet<>();
    collect(root, rows, value); // Collect matching row IDs starting from the root page of the index.
    return rows.stream().toList(); // Return collected row IDs as a list.
  }
}