package antdb.storage;

import antdb.query.Value;
import antdb.sql.AST;
import antdb.sql.Parser;
import antdb.sql.Scanner;

import java.util.*;
import java.util.stream.IntStream;

// The Table class represents a database table and provides methods for interacting with its data.
// It manages the structure of the table, including its columns and types, and handles data retrieval.
public class Table {
  private final StorageEngine storage; // Reference to the storage engine managing database operations.
  private final String name; // The name of the table as a string.
  private final Page.TablePage root; // This page contains the actual data for the table.
  private final AST.CreateTableStatement definition; // Defines the structure of the table, including its columns and types.

  // Constructor initializes the Table object with necessary parameters.
  public Table(StorageEngine storage, String name, Page.TablePage root, String schema) {
    this.storage = storage; // Store reference to the storage engine.
    this.name = name; // Store the name of the table.
    this.root = root; // Store reference to the root page containing table data.
    // Parse schema definition into an AST representation for table creation.
    this.definition = new Parser(new Scanner(schema)).createTable();
  }

  // Checks if a column is defined as an integer primary key.
  private static boolean isIntegerPK(AST.ColumnDef col) {
    List<String> mods = col.modifiers(); // Get modifiers associated with the column definition.
    // Returns true if the column is defined as an integer primary key; otherwise, false.
    return mods.contains("integer") && mods.contains("primary") && mods.contains("key");
  }

  // Parses a row from a given Page.Row object into a Row object with values mapped by column names.
  private Row parseRow(Page.Row row) {
      HashMap<String, Value> record = new HashMap<>(); // Create a map to hold column values.
      // Use row ID as value if it's an integer primary key; otherwise get value from row data.
      IntStream.range(0, definition.columns().size()).forEach(i -> {
          AST.ColumnDef col = definition.columns().get(i); // Get column definition from the schema.
          Value val = isIntegerPK(col)
                  ? new Value.IntValue(row.rowId()) // Assign row ID if it's a primary key
                  : row.values().values().get(i); // Otherwise get value from row data.
          record.put(col.name(), val); // Map column name to its corresponding value in the record.
      });
    return new Row(row.rowId(), record); // Return a new Row object with ID and values.
  }

  // Collects all rows from a given table page (leaf or interior) and adds them to a list.
  private void collect(Page.TablePage page, List<Row> rows) {
    switch (page) {
      // If it's a leaf page, collect records directly by parsing them into Row objects.
      case Page.TableLeafPage leaf ->
              leaf.records()
                      .map(this::parseRow)
                      .forEach(rows::add);
      // If it's an interior page, recursively collect child pages until leaf pages are reached.
      case Page.TableInteriorPage interior ->
              interior.records()
                      .forEach(child -> collect(
                              storage.getPage(child.pageNumber()).asTablePage(), rows));
    }
  }

  // Determines if a given row ID is within the bounds defined by a pointer structure.
  private static boolean contains(Pointer<Long> page, long rowId) {
    // Check if rowId is less than endpoint on left side
    if (page.left() instanceof Pointer.Bounded<Long>(Long endpoint) && rowId < endpoint) {
      return false; // Return false if rowId is less than left endpoint
    }
    // Check right side boundary conditions
    // Return true if no right endpoint or if rowId is less than or equal to right endpoint
    return !(page.right() instanceof Pointer.Bounded<Long>(Long endpoint)) || rowId <= endpoint;
  }

  // Looks up a specific row by its ID within a given table page (either interior or leaf).
  private Optional<Row> lookup(Page.TablePage page, long rowId) {
    return switch (page) {
      // For interior pages, filter child records based on whether they contain the specified row ID.
      case Page.TableInteriorPage interior ->
              interior.records()
                      .filter(child -> contains(child, rowId))
                      .findFirst()
                      .flatMap(child -> lookup(
                              storage.getPage(child.pageNumber()).asTablePage(), rowId));
      // For leaf pages, find the specific record matching the given row ID and parse it into a Row object.
      case Page.TableLeafPage leaf ->
              leaf.records()
                      .filter(child -> child.rowId() == rowId)
                      .map(this::parseRow)
                      .findFirst();
    };
  }

  // Represents individual records in a table with associated values by column names.
  public record Row(long rowId, Map<String, Value> values) {
    public Value get(String column) { return values.get(column); }
    // Retrieves value by column name from this Row's values map.
  }

  public String name() { return name; }  // Returns the name of this table.

  public List<Row> rows() {
    ArrayList<Row> rows = new ArrayList<>();  // List to hold all rows in this table.
    collect(root, rows);  // Collect all rows starting from the root page of this table.
    return rows;  // Return collected rows as a list.
  }

  public Optional<Row> get(long rowId) {
    return lookup(root, rowId);  // Lookup specified row ID starting from root page and return it as an Optional.
  }
}