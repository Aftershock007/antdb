package antdb.sql;

import java.util.Optional;

import static antdb.sql.Token.Type.*;

// The Scanner is responsible for converting a raw SQL string into tokens.
// It reads characters from the SQL query and identifies tokens based on their types.
// It handles whitespace, recognizes special characters like parentheses and operators, and identifies identifiers (like table names or column names) and string literals.
public class Scanner {
  private final String s; // The SQL query string to be scanned
  private int pos; // Current position in the string being scanned
  private Optional<Token> lookahead; // Lookahead token for peeking at the next token

  public Scanner(String s) {
    this.s = s;
    this.pos = 0; // Start scanning from the beginning of the string
    this.lookahead = Optional.empty();
  }

  // Checks if a character is part of an identifier (letters or underscore)
  private static boolean isIdentifier(char c) {
    // Valid identifiers start with letters or underscore
    return Character.isAlphabetic(c) || c == '_';
  }

  // Tries to get the keyword type from a given string name.
  private static Optional<Token.Type> getKeyword(String name) {
    try {
      // Convert keyword to uppercase and get its type
      return Optional.of(Token.Type.valueOf(name.toUpperCase()));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  // Returns the type of the token based on its character
  private static Token.Type getType(char c) {
    return switch (c) {
      case ',' -> COMMA; // Comma token
      case '=' -> EQ; // Equals token
      case '(' -> LPAREN; // Left parenthesis token
      case ')' -> RPAREN; // Right parenthesis token
      case '*' -> STAR; // Asterisk token (for SELECT *)
      default -> throw new SQLException("scanner: bad token: %c".formatted(c)); // Invalid character encountered
    };
  }

  // Consumes a character from the input if it matches the expected character.
  private void eat(char want) {
    // Check if we are at the end of the input string
    if (pos >= s.length()) {
      // Throw exception if end of file reached unexpectedly
      throw new SQLException("scanner: unexpected eof");
    }
    char got = s.charAt(pos); // Get the current character
    if (got != want) { // Check if it matches the expected character
      throw new SQLException("scanner: want %c, got %c".formatted(want, got)); // Throw exception if mismatch occurs
    }
    ++pos; // Move to the next character in the input string
  }

  // Reads an identifier from the input until a non-identifier character is encountered.
  private Token identifier() {
    // Mark the beginning position of the identifier
    int begin = pos;
    // Continue until a non-identifier character is found
    while (pos < s.length() && isIdentifier(s.charAt(pos))) {
      pos++;
    }
    // Extract the identifier text from the input string
    String text = s.substring(begin, pos);
    // Return a Token of type IDENT or a recognized keyword type if applicable.
    return getKeyword(text).map(Token::of).orElse(Token.of(Token.Type.IDENT, text));
  }

  // Reads a string literal from the input, delimited by specified characters.
  private String stringLiteral(char delim) {
    // Consume the opening delimiter (either ' or ")
    eat(delim);
    // Mark beginning of string literal
    int begin = pos;
    // Read until closing delimiter is found
    while (pos < s.length() && s.charAt(pos) != delim) {
      ++pos;
    }
    // Extract string literal text from input string
    String text = s.substring(begin, pos);
    eat(delim); // Consume closing delimiter
    return text; // Return extracted string literal
  }

  // Checks if we have reached the end of input.
  public boolean isEof() {
    return peek().isEmpty(); // Returns true if there are no more tokens to read.
  }

  // Peeks at the next token without consuming it.
  public Optional<Token> peek() {
    // If no lookahead token exists, advance to get one.
    if (lookahead.isEmpty()) {
      lookahead = advance();
    }
    return lookahead; // Return current lookahead token.
  }

  // Advances to the next token in the input string and returns it.
  public Token next() {
    // Throws exception if there are no more tokens available.
    return advance().orElseThrow(() -> new SQLException("unexpected eof"));
  }

  // Processes characters to identify tokens and returns them as needed.
  private Optional<Token> advance() {
    if (lookahead.isPresent()) {
      Optional<Token> tok = lookahead;
      lookahead = Optional.empty();
      // If there is a lookahead token, return it and clear lookahead.
      return tok;
    }
    while (pos < s.length()) {
      char c = s.charAt(pos);
      switch (c) {
        case ' ', '\n', '\t' -> ++pos;
        case '\'' -> {
          // Handle single-quoted strings as STR tokens.
          return Optional.of(Token.of(Token.Type.STR, stringLiteral(c)));
        }
        case '"' -> {
          // Handle double-quoted identifiers as IDENT tokens.
          return Optional.of(Token.of(Token.Type.IDENT, stringLiteral(c)));
        }
        case '=', ',', '(', ')', '*' -> {
          eat(c);
          // Handle special characters as their respective token types.
          return Optional.of(Token.of(getType(c)));
        }
        default -> {
          // Handle identifiers or throw an error for invalid characters.
          if (isIdentifier(c)) {
            return Optional.of(identifier());
          }
          else {
            throw new SQLException("scanner: bad token: %c".formatted(c));
          }
        }
      }
    }
    return Optional.empty(); // Return empty if no more tokens are available.
  }
}
