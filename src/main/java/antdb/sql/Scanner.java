package antdb.sql;

import java.util.Optional;

import static antdb.sql.Token.Type.*;

public class Scanner {
  private final String s;
  private int pos;
  private Optional<Token> lookahead;

  public Scanner(String s) {
    this.s = s;
    this.pos = 0;
    this.lookahead = Optional.empty();
  }

  private static boolean isIdentifier(char c) {
    return Character.isAlphabetic(c) || c == '_';
  }

  private static Optional<Token.Type> getKeyword(String name) {
    try {
      return Optional.of(Token.Type.valueOf(name.toUpperCase()));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  private static Token.Type getType(char c) {
    return switch (c) {
      case ',' -> COMMA;
      case '=' -> EQ;
      case '(' -> LPAREN;
      case ')' -> RPAREN;
      case '*' -> STAR;
      default -> throw new SQLException("scanner: bad token: %c".formatted(c));
    };
  }

  private void eat(char want) {
    if (pos >= s.length()) {
      throw new SQLException("scanner: unexpected eof");
    }
    char got = s.charAt(pos);
    if (got != want) {
      throw new SQLException("scanner: want %c, got %c".formatted(want, got));
    }
    ++pos;
  }

  private Token identifier() {
    int begin = pos;
    while (pos < s.length() && isIdentifier(s.charAt(pos))) pos++;
    var text = s.substring(begin, pos);
    return getKeyword(text).map(Token::of).orElse(Token.of(IDENT, text));
  }

  private String stringLiteral(char delim) {
    eat(delim);
    int begin = pos;
    while (pos < s.length() && s.charAt(pos) != delim) ++pos;
    var text = s.substring(begin, pos);
    eat(delim);
    return text;
  }

  public boolean isEof() {
    return peek().isEmpty();
  }

  public Optional<Token> peek() {
    if (lookahead.isEmpty()) lookahead = advance();
    return lookahead;
  }

  public Token next() {
    return advance().orElseThrow(() -> new SQLException("unexpected eof"));
  }

  private Optional<Token> advance() {
    if (lookahead.isPresent()) {
      var tok = lookahead;
      lookahead = Optional.empty();
      return tok;
    }
    while (pos < s.length()) {
      char c = s.charAt(pos);
      switch (c) {
        case ' ', '\n', '\t' -> ++pos;
        case '\'' -> {
          return Optional.of(Token.of(STR, stringLiteral(c)));
        }
        case '"' -> {
          return Optional.of(Token.of(IDENT, stringLiteral(c)));
        }
        case '=', ',', '(', ')', '*' -> {
          eat(c);
          return Optional.of(Token.of(getType(c)));
        }
        default -> {
          if (isIdentifier(c)) return Optional.of(identifier());
          else throw new SQLException("scanner: bad token: %c".formatted(c));
        }
      }
    }
    return Optional.empty();
  }
}
