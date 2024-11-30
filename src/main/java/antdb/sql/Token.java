package antdb.sql;

// The Token class represents a single token in the SQL query parsing process.
// Each token consists of a type and an optional text value.
public record Token(Token.Type type, String text) {
  // Factory method to create a Token with both type and text.
  public static Token of(Token.Type type, String text) {
    return new Token(type, text); // Create and return a new Token instance.
  }

  // Factory method to create a Token with only type (text will be null).
  public static Token of(Token.Type type) {
    return of(type, null); // Call the other factory method with null for text.
  }

  // The Type enum defines all possible types of tokens that can be recognized in SQL queries.
  public enum Type {
    SELECT, // Represents the SELECT keyword.
    FROM,   // Represents the FROM keyword.
    LPAREN,  // Represents the left parenthesis '('.
    RPAREN,  // Represents the right parenthesis ')'.
    STAR,   // Represents the wildcard '*' used in SELECT statements.
    CREATE, // Represents the CREATE keyword for creating tables or indices.
    TABLE,  // Represents the TABLE keyword in CREATE statements.
    INDEX,  // Represents the INDEX keyword in CREATE INDEX statements.
    COMMA,  // Represents the comma ',' used to separate items in lists.
    WHERE,  // Represents the WHERE keyword used for filtering results.
    ON,     // Represents the ON keyword used in JOIN conditions or CREATE INDEX statements.
    EQ,     // Represents the equality operator '=' used in conditions.
    IDENT,  // Represents identifiers such as table names or column names.
    STR     // Represents string literals enclosed in quotes.
  }
}