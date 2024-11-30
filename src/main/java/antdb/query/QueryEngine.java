package antdb.query;

import antdb.sql.AST;
import antdb.sql.Parser;
import antdb.sql.SQLException;
import antdb.sql.Scanner;
import antdb.storage.Index;
import antdb.storage.StorageEngine;
import antdb.storage.StorageException;
import antdb.storage.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// The QueryEngine class is responsible for executing SQL queries against a database.
// It interacts with the database's storage engine, processes SQL statements, and retrieves data based on user queries.
public class QueryEngine {
  // The storage engine that manages database operations
  private final StorageEngine db;

  // Constructor that initializes the QueryEngine with a StorageEngine instance.
  public QueryEngine(StorageEngine db) {
    this.db = db; // Store the reference to the storage engine for later use.
  }

  // Check if the expression is an aggregation function (like COUNT).
  private static boolean isAggregation(AST.Expr expr) {
    return expr instanceof AST.FnCall; // Returns true if the expression is a function call (indicating aggregation).
  }

  /**
   * Evaluate an expression against a list of rows.
   * Example SQL Command:
   * - SELECT COUNT(*) FROM users; // Count the number of users
   */
  private Value evaluate(AST.Expr expr, List<Table.Row> rows) throws SQLException {
    return switch (expr) {
      case AST.FnCall(String fn, List<?> ignored) when fn.equals("count") ->
              new Value.IntValue(rows.size()); // Return the count of rows if COUNT function is called.
      case AST.Expr ignored when rows.isEmpty() ->
              new Value.NullValue(); // Return null if there are no rows to evaluate.
      default -> evaluate(expr, rows.getFirst()); // Evaluate expression against the first row.
    };
  }

  /**
   * Evaluate an expression against a single row.
   * Example SQL Command:
   * - SELECT username FROM users WHERE id = 1; // Retrieve username for user with ID 1
   */
  private Value evaluate(AST.Expr expr, Table.Row row) throws SQLException {
    return switch (expr) {
      case AST.ColumnName(String name) ->
              row.get(name); // Get value from the row by column name.
      case AST.StrLiteral(String s) ->
              new Value.StringValue(s); // Return string literal value as StringValue.
      default ->
              throw new SQLException("invalid expr: %s".formatted(expr)); // Handle invalid expressions with an error.
    };
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws SQLException; // Functional interface for handling SQL exceptions in lambda expressions.
  }

  // Wrap SQL exceptions in a runtime exception for easier handling.
  private <T> T wrapSQLException(ThrowingSupplier<T> supplier) {
    try {
      return supplier.get(); // Call the supplier's get method to execute potentially throwing code.
    } catch (SQLException e) {
      throw new RuntimeException(e); // Wrap and rethrow as a runtime exception if SQLException occurs.
    }
  }

  /**
   * Evaluate a list of expressions against a list of rows.
   * Example SQL Command:
   * - SELECT id, username FROM users; // Retrieve id and username for all users
   */
  private List<Row> evaluate(List<AST.Expr> cols, List<Table.Row> rows) throws SQLException {
    List<Row> results = new ArrayList<>(); // List to hold results of evaluated rows.
    // Check for aggregation functions in the selected columns.
    if (cols.stream().anyMatch(QueryEngine::isAggregation)) {
      // If any column is an aggregation function, evaluate them collectively.
      List<Value> result = cols.stream()
              .map(col -> wrapSQLException(() -> evaluate(col, rows))) // Evaluate each column expression using wrapSQLException for error handling.
              .toList();
      results.add(new Row(result)); // Add aggregated result as a single Row to results list.
    } else {
        // For each row in the provided list of rows
        // Add evaluated row to results list.
        results = rows.stream().map(row -> cols.stream()
                .map(col -> wrapSQLException(() -> evaluate(col, row))) // Evaluate each column expression for this specific row.
                .toList()).map(Row::new).collect(Collectors.toList());
    }
    return results; // Return all evaluated results as a list of Rows.
  }

  /**
   * Evaluate a filter condition against a single row.
   * Example SQL Command:
   * - SELECT * FROM users WHERE age > 18; // Check if user is older than 18
   */
  private boolean evaluate(AST.Filter filter, Table.Row row) throws SQLException {
    // Check if the value in the specified column matches the filter value.
    return evaluate(filter.column(), row).equals(evaluate(filter.value(), row));
  }

  // Find an index that matches the filter condition if available.
  private Optional<Index> findIndexForFilter(AST.Filter f) throws SQLException, StorageException {
    return db.getIndices()
            .stream()
            .filter(idx -> idx.definition().column().equals(f.column().name()))
            // Look for matching index by column name in the filter condition
            .findFirst(); // Return first matching index if found
  }

  // Convert AST literal to Value type for comparison or retrieval.
  private static Value valueOf(AST.Literal literal) {
    if (literal instanceof AST.StrLiteral(String s)) {
      return new Value.StringValue(s); // Convert string literal to Value.StringValue type
    }
    // Throw exception for unsupported literal types
    throw new IllegalArgumentException("unimplemented");
  }

  /**
   * Retrieve rows from a table based on filter conditions using either index or full scan.
   * Example SQL Command:
   * - SELECT * FROM users WHERE username = 'john'; // Retrieve users where username is 'john'
   */
  private List<Table.Row> getRows(Table table, AST.Filter filter) throws SQLException, StorageException {
    Optional<Index> maybeIndex = findIndexForFilter(filter);
    if (maybeIndex.isPresent()) {
      // Get matching record IDs from index based on filter condition
      List<Long> rowIds = maybeIndex.get().findMatchingRecordIds(
              filter.column().name(),
              valueOf(filter.value())
      );
        // Add each found row to results or throw error if not found
        return rowIds.stream().mapToLong(rowId -> rowId).mapToObj(rowId -> table.get(rowId).orElseThrow(() -> new AssertionError("row not found in table for indexed id %d".formatted(rowId)))).collect(Collectors.toList());
    } else {
        // If the row matches the filter condition, add it to results list
        return table.rows().stream().filter(row -> evaluate(filter, row)).collect(Collectors.toList());
    }
  }

  /**
   * Evaluate different types of statements based on their type (e.g., SELECT).
   */
  private List<Row> evaluate(AST.Statement statement) throws SQLException, StorageException {
    switch (statement) {
      // Currently not supporting CREATE TABLE statements
      case AST.CreateTableStatement ignored -> throw new SQLException("table creation not supported");
      // Currently not supporting CREATE INDEX statements
      case AST.CreateIndexStatement ignored -> throw new SQLException("index creation not supported");
      case AST.SelectStatement(List<AST.Expr> cols,
                               Optional<AST.Filter> cond,
                               String tableName) -> {
        Table table = db.getTables()
                .stream()
                .filter(t -> t.name().equals(tableName))
                .findAny()
                .orElseThrow(() -> new SQLException("no such table: %s".formatted(tableName)));
        List<Table.Row> rows = cond.map(filter -> getRows(table, filter))
                .orElseGet(table::rows);
        return evaluate(cols, rows);  // Evaluate selected columns against retrieved rows
      }
    }
  }

  /**
   * Evaluate the provided SQL statement string and return the resulting rows.
   * Example SQL Command:
   * - SELECT username FROM users WHERE age > 18; // Retrieve usernames of users older than 18.
   */
  public List<Row> evaluate(String statement) throws SQLException, StorageException {
    // Parse statement and evaluate it using Parser and Scanner classes
    return evaluate(new Parser(new Scanner(statement)).statement());
  }
}