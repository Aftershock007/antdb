package antdb.database;

import antdb.query.QueryEngine;
import antdb.query.Row;
import antdb.query.Value;
import antdb.sql.SQLException;
import antdb.storage.*;

import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// AutoCloseable is used to define a resource that can be closed automatically after its usage
public class Database implements AutoCloseable {
  private static final System.Logger log = System.getLogger(
      Database.class.getCanonicalName()
  );

  private final BackingFile backingFile;

  private Database(SeekableByteChannel backingFile) {
    this.backingFile = new BackingFile(backingFile);
  }

  private static void die(Exception e) {
    log.log(System.Logger.Level.ERROR, "antdb: fatal error", e);
    System.exit(1);
  }

  public void close() {
    backingFile.close();
  }

  private void dbinfo() {
    StorageEngine storage = new StorageEngine(backingFile);
    storage.getInfo()
            .forEach(
                    (field, val) -> System.out.printf("%s: %s\n", field, val)
            );
  }

  private void tables() {
    StorageEngine storage = new StorageEngine(backingFile);
    List<String> names = storage
            .getTables()
            .stream()
            .map(Table::name)
            .filter(name -> !name.startsWith("sqlite_"))
            .toList();
    System.out.println(String.join(" ", names));
  }

  private void schema() {
    StorageEngine storage = new StorageEngine(backingFile);
    for (Map<String, String> object : storage.getObjects()) {
      for (Map.Entry<String, String> entry : object.entrySet()) {
        System.out.printf("%s: '%s'\n".formatted(entry.getKey(), entry.getValue()));
      }
      System.out.println();
    }
  }

  private void indices() {
    StorageEngine storage = new StorageEngine(backingFile);
    List<Index> indices = storage.getIndices();
    for (Index index : indices) {
      System.out.printf("index: %s\n".formatted(index.name()));
      System.out.printf("table: %s\n".formatted(index.table().name()));
      System.out.printf("fields: %s\n".formatted(index.definition().column()));
    }
  }

  private void query(
          String command
  ) throws SQLException, StorageException {
    StorageEngine storage = new StorageEngine(backingFile);
    QueryEngine query = new QueryEngine(storage);
    List<Row> results = query.evaluate(command);
    for (Row row : results) {
      List<String> values = row.columns().stream().map(Value::display).toList();
      System.out.println(String.join("|", values));
    }
  }

  private static void run(
          String path,
          String command
  ) {
    try {
      SeekableByteChannel databasePath = Files.newByteChannel(Path.of(path));
      Database db = new Database(databasePath);
      switch (command) {
        case ".dbinfo" -> db.dbinfo();
        case ".tables" -> db.tables();
        case ".indices" -> db.indices();
        case ".schema" -> db.schema();
        default -> db.query(command);
      }
    } catch (Exception e) {
      die(e);
    }
  }

  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println("Incorrect command");
      System.exit(1);
    }
    String path = args[0], command = args[1];
    run(path, command);
  }
}
