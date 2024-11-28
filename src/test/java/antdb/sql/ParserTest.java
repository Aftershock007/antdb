package antdb.sql;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static antdb.sql.AST.*;

public class ParserTest {
  private static Parser parse(String text) {
    return new Parser(new Scanner(text));
  }

  @Test
  public void testCreateIndex() {
    assertEquals(
        new CreateIndexStatement("byEmail", "users", "email"),
        parse(" create index byEmail on users (email) ").createIndex());
  }

  @Test
  public void testCreateIndexErrors() {
    assertThrows(
        SQLException.class,
        () -> parse("create index byEmail").createIndex());
    assertThrows(
        SQLException.class,
        () -> parse("create index byEmail (email)").createIndex());
    assertThrows(
        SQLException.class,
        () -> parse("create index byEmail on users").createIndex());
    assertThrows(
        SQLException.class,
        () -> parse("create index byEmail on users ()").createIndex());
    assertThrows(
        SQLException.class,
        () -> parse("create index byEmail on users (foo").createIndex());
    assertThrows(
        SQLException.class,
        () -> parse("create index byEmail on users (foo) bar").createIndex());
  }

  @Test
  public void testCreateTableEmpty() {
    assertEquals(
        new CreateTableStatement("users", List.of()),
        parse("create table users ()").createTable());
  }

  @Test
  public void testCreateTableOneColumn() {
    assertEquals(
        new CreateTableStatement(
            "users",
            List.of(new ColumnDef("email", List.of("string", "nonnull")))),
        parse("create table users (email string nonnull)").createTable());
  }

  @Test
  public void testCreateTable() {
    assertEquals(
        new AST.CreateTableStatement(
            "users",
            List.of(
                new ColumnDef("id", List.of("int", "primary", "key")),
                new ColumnDef("name", List.of("string", "nonnull")),
                new ColumnDef("email", List.of("string", "nonnull")),
                new ColumnDef("birthdate", List.of("date")),
                new ColumnDef("bio", List.of("string"))
            )
        ),
        parse("""
                  create table users (
                      id int primary key,
                      name string nonnull,
                      email string nonnull,
                      birthdate date,
                      bio string
                  )""").createTable()
    );
  }

  @Test
  public void testCreateTableErrors() {
    assertThrows(
        SQLException.class,
        () -> parse("create table foo").createTable());
    assertThrows(
        SQLException.class,
        () -> parse("create table foo (").createTable());
    assertThrows(
        SQLException.class,
        () -> parse("create table foo ( user 5 )").createTable());
    assertThrows(
        SQLException.class,
        () -> parse("create table foo ( 5 )").createTable());
    assertThrows(
        SQLException.class,
        () -> parse("create table foo ( 5 ) foo").createTable());
  }

  @Test
  public void testSelectStar() {
    assertEquals(
        new SelectStatement(List.of(new Star()), Optional.empty(), "users"),
        parse(" select * from users ").select()
    );
  }

  @Test
  public void testSelectFnCall() {
    assertEquals(
        new SelectStatement(
            List.of(new FnCall("max", List.of(new ColumnName("birthdate")))),
            Optional.empty(),
            "users"),
        parse("select max(birthdate) from users").select()
    );
  }

  @Test
  public void testSelectColumn() {
    assertEquals(
        new SelectStatement(
            List.of(new ColumnName("name")),
            Optional.empty(),
            "users"),
        parse("select name from users").select()
    );
  }

  @Test
  public void testSelectLiteral() {
    assertEquals(
        new SelectStatement(
            List.of(new StrLiteral("foo")),
            Optional.empty(),
            "users"),
        parse("select 'foo' from users").select()
    );
  }

  @Test
  public void testSelectMultipleResults() {
    assertEquals(
        new SelectStatement(
            List.of(
                new ColumnName("name"),
                new FnCall("len", List.of(new ColumnName("email"))),
                new FnCall("count", List.of(new Star())),
                new StrLiteral("foo"),
                new Star()
            ),
            Optional.empty(),
            "users"),
        parse("select name, len(email), count(*), 'foo', * from users").select()
    );
  }

  @Test
  public void testSelectWithFilter() {
    assertEquals(
        new SelectStatement(
            List.of(new FnCall("count", List.of(new ColumnName("name")))),
            Optional.of(new Filter(new ColumnName("birthplace"),
                                   new StrLiteral("nyc"))),
            "users"
        ),
        parse("select count(name) from users where birthplace = 'nyc'").select()
    );
  }
}
