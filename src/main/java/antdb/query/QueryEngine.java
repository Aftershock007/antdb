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

public class QueryEngine {
  private final StorageEngine db;

  public QueryEngine(StorageEngine db) {
    this.db = db;
  }

  private static boolean isAggregation(AST.Expr expr) {
    return expr instanceof AST.FnCall;
  }

  private Value evaluate(
          AST.Expr expr,
          List<Table.Row> rows
  ) throws SQLException {
    return switch (expr) {
      case AST.FnCall(String fn, List<?> ignored) when fn.equals("count") -> new Value.IntValue(rows.size());
      case AST.Expr ignored when rows.isEmpty() -> new Value.NullValue();
      default -> evaluate(expr, rows.getFirst());
    };
  }

  private Value evaluate(
          AST.Expr expr,
          Table.Row row
  ) throws SQLException {
    return switch (expr) {
      case AST.ColumnName(String name) -> row.get(name);
      case AST.StrLiteral(String s) -> new Value.StringValue(s);
      default -> throw new SQLException("invalid expr: %s".formatted(expr));
    };
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws SQLException;
  }

  private <T> T wrapSQLException(ThrowingSupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private List<Row> evaluate(
          List<AST.Expr> cols,
          List<Table.Row> rows
  ) throws SQLException {
    List<Row> results = new ArrayList<>();
    if (cols.stream().anyMatch(QueryEngine::isAggregation)) {
      List<Value> result = cols.stream()
              .map(col -> wrapSQLException(() -> evaluate(col, rows)))
              .toList();
      results.add(new Row(result));
    } else {
      for (Table.Row row : rows) {
        List<Value> result = cols.stream()
                .map(col -> wrapSQLException(() -> evaluate(col, row)))
                .toList();
        results.add(new Row(result));
      }
    }
    return results;
  }

  private boolean evaluate(
          AST.Filter filter,
          Table.Row row
  ) throws SQLException {
    return evaluate(filter.column(), row).equals(evaluate(filter.value(), row));
  }

  private Optional<Index> findIndexForFilter(
          AST.Filter f
  ) throws SQLException, StorageException {
    return db.getIndices()
            .stream()
            .filter(idx -> idx.definition().column().equals(f.column().name()))
            .findFirst();
  }

  private static Value valueOf(AST.Literal literal) {
    if (literal instanceof AST.StrLiteral(String s)) {
      return new Value.StringValue(s);
    }
    throw new IllegalArgumentException("unimplemented");
  }

  private List<Table.Row> getRows(
          Table table,
          AST.Filter filter
  ) throws SQLException, StorageException {
    Optional<Index> maybeIndex = findIndexForFilter(filter);
    if (maybeIndex.isPresent()) {
      List<Long> rowIds = maybeIndex
              .get()
              .findMatchingRecordIds(
                      filter.column().name(),
                      valueOf(filter.value())
              );
      List<Table.Row> results = new ArrayList<>();
      for (long rowId : rowIds) {
        results.add(
                table
                .get(rowId)
                .orElseThrow(() -> new AssertionError("row not found in table for indexed id %d".formatted(rowId)))
        );
      }
      return results;
    } else {
      List<Table.Row> results = new ArrayList<>();
      for (Table.Row row : table.rows()) {
        if (evaluate(filter, row)) {
          results.add(row);
        }
      }
      return results;
    }
  }

  private List<Row> evaluate(
          AST.Statement statement
  ) throws SQLException, StorageException {
    switch (statement) {
      case AST.CreateTableStatement ignored ->
          throw new SQLException("table creation not supported");
      case AST.CreateIndexStatement ignored ->
          throw new SQLException("index creation not supported");
      case AST.SelectStatement(List<AST.Expr> cols,
                               Optional<AST.Filter> cond,
                               String tableName) -> {
        Table table = db.getTables()
                .stream()
                .filter(t -> t.name().equals(tableName))
                .findAny()
                .orElseThrow(() -> new SQLException("no such table: %s".formatted(tableName)));
        List<Table.Row> rows = cond
                .map(filter -> getRows(table, filter))
                .orElseGet(table::rows);
        return evaluate(cols, rows);
      }
    }
  }

  // TODO: stream
  public List<Row> evaluate(
          String statement
  ) throws SQLException, StorageException {
    return evaluate(new Parser(new Scanner(statement)).statement());
  }
}
