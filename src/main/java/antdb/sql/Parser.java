package antdb.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static antdb.sql.AST.*;
import static antdb.sql.Token.Type.*;

public class Parser {
  private final Scanner scanner;

  public Parser(Scanner scanner) {
    this.scanner = scanner;
  }

  private boolean peekIs(Token.Type type) {
    return scanner.peek().map(tok -> tok.type() == type).orElse(false);
  }

  private void eof() {
    scanner.peek().ifPresent(tok -> {
      throw new SQLException("parser: expected eof, got %s".formatted(tok));
    });
  }

  private Token eat(Token.Type type) {
    var tok = scanner.next();
    if (tok.type() != type) {
      throw new SQLException("parser: want %s, got %s".formatted(type, tok));
    }
    return tok;
  }

  private FnCall fnCall(String name) {
    eat(LPAREN);
    var arg = this.expr();
    eat(RPAREN);
    return new FnCall(name.toLowerCase(), List.of(arg));
  }

  private Expr expr() {
    var tok = scanner.next();
    var text = tok.text();
    return switch (tok.type()) {
      case STR -> new StrLiteral(text);
      case STAR -> new Star();
      case IDENT -> peekIs(LPAREN) ? fnCall(text) : new ColumnName(text);
      default -> throw new SQLException("parser: bad expr: %s".formatted(tok));
    };
  }

  private Filter cond() {
    eat(WHERE);
    var left = switch (expr()) {
      case ColumnName columnName -> columnName;
      case Expr e ->
          throw new SQLException("want ColumnName, got %s".formatted(e));
    };
    eat(EQ);
    var right = switch (expr()) {
      case Literal lit -> lit;
      case Expr e ->
          throw new SQLException("want Literal, got %s".formatted(e));
    };
    return new Filter(left, right);
  }

  public SelectStatement select() {
    eat(SELECT);
    List<Expr> columns = new ArrayList<>();
    while (!peekIs(FROM)) {
      columns.add(expr());
      if (!peekIs(FROM)) eat(COMMA);
    }
    eat(FROM);
    var table = eat(IDENT);
    var filter = peekIs(WHERE) ? Optional.of(cond()) : Optional.<Filter>empty();
    eof();
    return new SelectStatement(columns, filter, table.text());
  }

  private ColumnDef columnDefinition() {
    var name = eat(IDENT);
    var modifiers = new ArrayList<String>();
    while (!peekIs(COMMA) && !peekIs(RPAREN)) {
      modifiers.add(eat(IDENT).text());
    }
    return new ColumnDef(name.text(), modifiers);
  }

  public CreateTableStatement createTable() {
    eat(CREATE);
    eat(TABLE);
    var name = eat(IDENT);
    eat(LPAREN);
    var columns = new ArrayList<ColumnDef>();
    while (!peekIs(RPAREN)) {
      columns.add(columnDefinition());
      if (!peekIs(RPAREN)) eat(COMMA);
    }
    eat(RPAREN);
    eof();
    return new CreateTableStatement(name.text(), columns);
  }

  public CreateIndexStatement createIndex() {
    eat(CREATE);
    eat(INDEX);
    var name = eat(IDENT);
    eat(ON);
    var table = eat(IDENT);
    eat(LPAREN);
    var column = eat(IDENT).text();
    eat(RPAREN);
    eof();
    return new CreateIndexStatement(name.text(), table.text(), column);
  }

  public Statement statement() {
    return peekIs(CREATE) ? createTable() : select();
  }
}
