import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import antdb.query.QueryEngine;
import antdb.query.Row;
import antdb.query.Value;
import antdb.sql.SQLException;
import antdb.storage.BackingFile;
import antdb.storage.StorageEngine;
import antdb.storage.StorageException;
import antdb.storage.Table;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IntegrationTest {
  private BackingFile file;

  @BeforeEach
  void setUp() throws IOException {
    var resource = Objects.requireNonNull(
        IntegrationTest.class.getResource("test.db"));
    file = new BackingFile(Files.newByteChannel(Path.of(resource.getFile())));
  }

  @AfterEach
  void tearDown() {
    file.close();
    file = null;
  }

  private List<Row> evaluate(String sql)
  throws SQLException, StorageException {
    var storage = new StorageEngine(file);
    return new QueryEngine(storage).evaluate(sql);
  }

  @Test
  void testDbinfo() {
    var storage = new StorageEngine(file);
    var info = storage.getInfo();
    assertEquals(4096, info.get("database page size"));
    assertEquals(2, info.get("number of tables"));
  }

  @Test
  void testTables() {
    var storage = new StorageEngine(file);
    assertEquals(
        storage.getTables().stream().map(Table::name)
               .collect(Collectors.toSet()),
        Set.of("companies", "sqlite_sequence"));
  }

  @Test
  void testCount() throws SQLException, StorageException {
    var rows = evaluate("SELECT count(*) FROM companies");
    assertEquals(rows, List.of(new Row(List.of(new Value.IntValue(55991)))));
  }

  @Test
  void testSelect() throws SQLException, StorageException {
    var names = evaluate(
        "SELECT name " +
        "FROM companies " +
        "WHERE locality = 'london, greater london, united kingdom'")
        .stream().map(row -> row.columns().getFirst().getString())
        .collect(Collectors.toSet());
    var expected = Set.of(
        "ascot barclay cyber security group",
        "align17",
        "intercash",
        "stonefarm capital llp",
        "blythe financial limited",
        "arnold wiggins & sons limited",
        "snowstream capital management ltd",
        "holmes&co | property",
        "tp international",
        "quantemplate",
        "clarity (previously hcl clarity)",
        "castille capital",
        "clayvard limited",
        "midnight tea studio",
        "tyntec",
        "trafalgar global",
        "transfertravel.com",
        "reign élan ltd");
    assertEquals(names, expected);
  }

  @Test
  void testIndex() throws SQLException, StorageException {
    var rows = new HashSet<>(evaluate(
        "SELECT id, name " +
        "FROM companies " +
        "WHERE country = 'republic of the congo'"));
    assertEquals(rows, Set.of(
        new Row(List.of(new Value.IntValue(517263),
                        new Value.StringValue("somedia"))),
        new Row(List.of(new Value.IntValue(509721),
                        new Value.StringValue("skytic telecom"))),
        new Row(List.of(new Value.IntValue(2995059),
                        new Value.StringValue(
                            "petroleum trading congo e&p sa"))),
        new Row(List.of(new Value.IntValue(2543747),
                        new Value.StringValue("its congo")))
    ));
  }
}
