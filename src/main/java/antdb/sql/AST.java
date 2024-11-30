package antdb.sql;

import java.util.List;
import java.util.Optional;

// The AST (Abstract Syntax Tree) class defines the structure of SQL statements and expressions in the database query language.
public class AST {
  // The Expr interface represents all types of expressions in SQL.
  public sealed interface Expr permits Star, FnCall, ColumnName, Literal {}

  // Represents a wildcard expression (e.g., SELECT *).
  public record Star() implements Expr {}

  // Represents a function call expression (e.g., COUNT(), SUM()).
  public record FnCall(String function, List<Expr> args) implements Expr {}

  // Represents a column name in SQL (e.g., username, age).
  public record ColumnName(String name) implements Expr {}

  // The Literal interface represents constant values in SQL (like strings).
  public sealed interface Literal extends Expr permits StrLiteral {}

  // Represents a string literal (e.g., 'Hello World').
  public record StrLiteral(String s) implements Literal {}

  // The Statement interface represents all types of SQL statements.
  public sealed interface Statement permits CreateIndexStatement, CreateTableStatement, SelectStatement {}

  // Represents a CREATE INDEX statement in SQL.
  public record CreateIndexStatement(
          String name,      // Name of the index
          String table,     // Table on which the index is created
          String column     // Column that is indexed
  ) implements Statement {}

  // Represents a CREATE TABLE statement in SQL.
  public record CreateTableStatement(
          String name,                     // Name of the table
          List<ColumnDef> columns          // List of column definitions for the table
  ) implements Statement {}

  // Represents a definition for a column in a CREATE TABLE statement.
  public record ColumnDef(String name, List<String> modifiers) {
    // Column name and any associated modifiers (like data type or constraints)
  }

  // Represents a SELECT statement in SQL.
  public record SelectStatement(
          List<Expr> results,              // List of expressions to select (columns)
          Optional<Filter> filter,          // Optional filter condition (WHERE clause)
          String table                      // Name of the table being queried
  ) implements Statement {}

  // Represents a filter condition in a WHERE clause.
  public record Filter(ColumnName column, Literal value) {
    // Column to filter on and the value to compare against
  }
}