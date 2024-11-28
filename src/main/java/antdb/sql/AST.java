package antdb.sql;

import java.util.List;
import java.util.Optional;

public class AST {
  public sealed interface Expr permits Star, FnCall, ColumnName, Literal {}
  public record Star() implements Expr {}
  public record FnCall(String function, List<Expr> args) implements Expr {}
  public record ColumnName(String name) implements Expr {}
  public sealed interface Literal extends Expr permits StrLiteral {}
  public record StrLiteral(String s) implements Literal {}

  public sealed interface Statement permits CreateIndexStatement, CreateTableStatement, SelectStatement {}

  public record CreateIndexStatement(
          String name,
          String table,
          String column
  ) implements Statement {}

  public record CreateTableStatement(
          String name, List<ColumnDef> columns
  ) implements Statement {}
  public record ColumnDef(String name, List<String> modifiers) {}

  public record SelectStatement(
      List<Expr> results, Optional<Filter> filter, String table
  ) implements Statement {}

  public record Filter(ColumnName column, Literal value) {}
}
