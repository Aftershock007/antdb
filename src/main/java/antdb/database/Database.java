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

// The Database class manages interactions with the database, including executing queries and retrieving metadata.
// It implements AutoCloseable to ensure that resources are released automatically when the database is closed.
public class Database implements AutoCloseable {
  // Logger for logging error messages and other information related to the database operations.
  private static final System.Logger log = System.getLogger(
          Database.class.getCanonicalName() // Retrieves the canonical name of the Database class for logging.
  );

  private final BackingFile backingFile; // The backing file that stores the database data.

  // Private constructor to restrict instantiation of Database from outside the class.
  // This pattern is often used in singleton designs or factory methods where control over instance creation is required.
  private Database(SeekableByteChannel backingFile) {
    this.backingFile = new BackingFile(backingFile); // Initialize the backing file.
  }

  // Logs a fatal error message and exits the application.
  private static void die(Exception e) {
    log.log(System.Logger.Level.ERROR, "antdb: fatal error", e); // Log the error.
    System.exit(1); // Terminate the application with a non-zero exit code.
  }

  // Closes the backing file associated with this Database instance, releasing resources.
  public void close() {
    backingFile.close(); // Call close on the BackingFile instance.
  }

  // Displays information about the database, such as page size and number of tables.
  private void dbinfo() {
    StorageEngine storage = new StorageEngine(backingFile); // Create a StorageEngine instance to manage data operations.
    storage.getInfo()
            .forEach((field, val) -> System.out.printf("%s: %s\n", field, val));
    // Print each field of information retrieved from the storage engine.
  }

  // Lists all tables in the database, excluding system tables that start with "sqlite_".
  private void tables() {
    StorageEngine storage = new StorageEngine(backingFile); // Create a StorageEngine instance.
    List<String> names = storage
            .getTables()
            .stream()
            .map(Table::name) // Map each Table object to its name.
            .filter(name -> !name.startsWith("sqlite_")) // Exclude system tables.
            .toList();
    System.out.println(String.join(" ", names)); // Print all table names joined by spaces.
  }

  // Displays the schema of all objects (tables and indices) in the database.
  private void schema() {
    StorageEngine storage = new StorageEngine(backingFile); // Create a StorageEngine instance.
      storage.getObjects().forEach(object -> {
          for (Map.Entry<String, String> entry : object.entrySet()) {
            // Print each object's metadata in key-value format.
            System.out.printf("%s: '%s'\n".formatted(entry.getKey(), entry.getValue()));
          }
          System.out.println(); // Print a blank line between objects for readability.
      });
  }

  // Displays information about all indices defined in the database.
  private void indices() {
    StorageEngine storage = new StorageEngine(backingFile); // Create a StorageEngine instance.
    List<Index> indices = storage.getIndices(); // Retrieve all indices from storage.
    // Print index details including its name, associated table, and indexed fields.
    indices.forEach(index -> {
      System.out.printf("index: %s\n".formatted(index.name()));
      System.out.printf("table: %s\n".formatted(index.table().name()));
      System.out.printf("fields: %s\n".formatted(index.definition().column()));
    });
  }

  // Executes a given SQL command and prints the results to standard output.
  private void query(String command) throws SQLException, StorageException {
    StorageEngine storage = new StorageEngine(backingFile); // Create a StorageEngine instance for executing queries.
    QueryEngine query = new QueryEngine(storage); // Create a QueryEngine instance to process SQL commands.
    List<Row> results = query.evaluate(command); // Evaluate the SQL command and retrieve results.
      // Print each row's values joined by pipes for clarity in output formatting.
      results.stream().map(row -> row.columns().stream().map(Value::display).toList()).map(values -> String.join("|", values)).forEach(System.out::println);
  }

  // Runs a specific command against a database located at a given path. Handles exceptions gracefully.
  private static void run(String path, String command) {
    try {
      SeekableByteChannel databasePath = Files.newByteChannel(Path.of(path));
      // Create a new Database instance with the specified file channel.
      Database db = new Database(databasePath);
      switch (command) {
        case ".dbinfo" -> db.dbinfo();  // Show database info if command is ".dbinfo".
        case ".tables" -> db.tables();   // Show tables if command is ".tables".
        case ".indices" -> db.indices();  // Show indices if command is ".indices".
        case ".schema" -> db.schema();   // Show schema if command is ".schema".
        default -> db.query(command);   // Execute any other SQL command provided by user input.
      }
    } catch (Exception e) {
      die(e);  // Handle any exceptions by logging and exiting the application.
    }
  }

  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println("Incorrect AntDB command");
      System.exit(1);  // Exit if insufficient arguments are provided by user input.
    }
    String path = args[0], command = args[1];
    run(path, command);  // Run the specified command against the database at the given path.
  }
}