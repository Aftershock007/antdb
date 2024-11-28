package antdb.sql;

public record Token(Token.Type type, String text) {
  public static Token of(Token.Type type, String text) {
    return new Token(type, text);
  }

  public static Token of(Token.Type type) {
    return of(type, null);
  }

  public enum Type {
    SELECT,
    FROM,
    LPAREN,
    RPAREN,
    STAR,
    CREATE,
    TABLE,
    INDEX,
    COMMA,
    WHERE,
    ON,
    EQ,
    IDENT,
    STR,
  }
}
