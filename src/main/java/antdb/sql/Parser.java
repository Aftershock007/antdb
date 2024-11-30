package antdb.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static antdb.sql.AST.*;
import static antdb.sql.Token.Type.*;

// The Parser takes tokens produced by the Scanner and constructs an Abstract Syntax Tree (AST).
public class Parser {
  // The scanner instance to read tokens from the input
  private final Scanner scanner;

  // Constructor initializes the parser with a Scanner instance
  public Parser(Scanner scanner) {
    this.scanner = scanner; // Set the scanner for token reading
  }

  // Checks if the next token matches a specific type without consuming it
  private boolean peekIs(Token.Type type) {
    // Returns true if the next token matches the specified type
    return scanner.peek().map(tok -> tok.type() == type).orElse(false);
  }

  // Throws SQLException if the end of input is reached unexpectedly
  private void eof() {
    scanner.peek().ifPresent(tok -> {
      // Error message for unexpected EOF
      throw new SQLException("parser: expected eof, got %s".formatted(tok));
    });
  }

  // Consumes a token of a specific type and returns it
  private Token eat(Token.Type type) {
    Token tok = scanner.next(); // Get the next token from the scanner
    // Check if the token type matches the expected type
    if (tok.type() != type) {
      // Throw an error if types do not match
      throw new SQLException("parser: want %s, got %s".formatted(type, tok));
    }
    return tok; // Return the consumed token
  }

  // Parses a function call expression (e.g., COUNT())
  private FnCall fnCall(String name) {
    eat(LPAREN); // Expect an opening parenthesis
    Expr arg = this.expr(); // Parse the argument expression
    eat(RPAREN); // Expect a closing parenthesis
    return new FnCall(name.toLowerCase(), List.of(arg)); // Return a function call AST node with the function name and its argument
  }

  // Parses an expression from tokens and returns it as an AST node
  private Expr expr() {
    Token tok = scanner.next(); // Get the next token
    String text = tok.text(); // Get the text of the token
    return switch (tok.type()) {
      case STR -> new StrLiteral(text); // Handle string literals
      case STAR -> new Star(); // Handle '*' for SELECT *
      case IDENT -> peekIs(LPAREN) ? fnCall(text) : new ColumnName(text); // Handle identifiers or function calls
      default -> throw new SQLException("parser: bad expr: %s".formatted(tok)); // Handle unexpected expression types
    };
  }

  // Parses a filter condition (e.g., WHERE column = value)
  private Filter cond() {
    eat(WHERE); // Expect WHERE keyword
    ColumnName left = switch (expr()) {
      case ColumnName columnName -> columnName; // Get left side of condition as ColumnName
      case Expr e -> throw new SQLException("want ColumnName, got %s".formatted(e)); // Error if not a ColumnName
    };
    eat(EQ); // Expect equality operator (=)
    Literal right = switch (expr()) {
      case Literal lit -> lit; // Get right side of condition as Literal value
      case Expr e -> throw new SQLException("want Literal, got %s".formatted(e)); // Error if not a Literal
    };
    return new Filter(left, right); // Return a Filter AST node with left and right expressions
  }

  // Parses a SELECT statement from tokens and returns it as an AST node
  public SelectStatement select() {
    eat(SELECT); // Expect SELECT keyword
    List<Expr> columns = new ArrayList<>(); // List to hold selected columns
    while (!peekIs(FROM)) {
      columns.add(expr()); // Parse each column expression until FROM keyword is found
      if (!peekIs(FROM)) eat(COMMA); // Consume comma between columns if present
    }
    eat(FROM); // Expect FROM keyword
    Token table = eat(IDENT); // Parse table name identifier
    // Parse filter condition if WHERE clause is present
    Optional<Filter> filter = peekIs(WHERE) ? Optional.of(cond()) : Optional.empty();
    eof(); // Check for end of input after parsing statement
    // Return constructed SelectStatement AST node with columns, filter, and table name
    return new SelectStatement(columns, filter, table.text());
  }

  // Parses a column definition for CREATE TABLE statements (e.g., id INTEGER PRIMARY KEY)
  private ColumnDef columnDefinition() {
    Token name = eat(IDENT); // Parse column name identifier
    ArrayList<String> modifiers = new ArrayList<>();
    while (!peekIs(COMMA) && !peekIs(RPAREN)) {
      // Collect modifiers like INTEGER, PRIMARY KEY until comma or closing parenthesis is found.
      modifiers.add(eat(IDENT).text());
    }
    // Return a ColumnDef AST node with column name and modifiers list.
    return new ColumnDef(name.text(), modifiers);
  }

  // Parses a CREATE TABLE statement and returns it as an AST node.
  public CreateTableStatement createTable() {
    eat(CREATE); // Expect CREATE keyword
    eat(TABLE);  // Expect TABLE keyword
    Token name = eat(IDENT);  // Parse table name identifier
    eat(LPAREN);  // Expect opening parenthesis for column definitions
    ArrayList<ColumnDef> columns = new ArrayList<>();  // List to hold column definitions
    while (!peekIs(RPAREN)) {
      columns.add(columnDefinition());  // Parse each column definition until closing parenthesis is found
      if (!peekIs(RPAREN)) {
        // Consume comma between column definitions if present.
        eat(COMMA);
      }
    }
    eat(RPAREN);  // Expect closing parenthesis
    eof();  // Check for end of input after parsing statement
    // Return constructed CreateTableStatement AST node with table name and columns list.
    return new CreateTableStatement(name.text(), columns);
  }

  // Parses a CREATE INDEX statement and returns it as an AST node.
  public CreateIndexStatement createIndex() {
    eat(CREATE); 	// Expect CREATE keyword.
    eat(INDEX);  	// Expect INDEX keyword.
    Token name = eat(IDENT); 	// Parse index name identifier.
    eat(ON);  	// Expect ON keyword.
    Token table = eat(IDENT);  	// Parse table name identifier.
    eat(LPAREN);  	// Expect opening parenthesis for index column definition.
    String column = eat(IDENT).text();  	// Parse indexed column identifier.
    eat(RPAREN);  	// Expect closing parenthesis.
    eof();  	// Check for end of input after parsing statement.
    // Return constructed CreateIndexStatement AST node with index name, table name, and indexed column.
    return new CreateIndexStatement(name.text(), table.text(), column);
  }

  // Determines which statement to parse based on the first token type.
  public Statement statement() {
    // If the next token is CREATE, parse as CreateTableStatement; otherwise parse as SelectStatement.
    return peekIs(CREATE) ? createTable() : select();
  }
}