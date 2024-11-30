package antdb.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// The StorageEngine class manages the overall storage and retrieval of data in the database.
// It handles reading from and writing to the underlying storage medium, as well as managing tables and indices.
public class StorageEngine {
  // SCHEMA: A SQL statement that defines the structure of the antdb_schema table.
  // This table stores metadata about other tables in the database, including their types, names, root pages, and SQL definitions.
  private static final String SCHEMA = """
            CREATE TABLE antdb_schema(
                type text,
                name text,
                tbl_name text,
                rootpage integer,
                sql text
            )
        """;

  private final int pageSize; // Size of each page in the database.
  private final BackingFile file; // The file that serves as the backing store for the database.
  // charset: The character set used for encoding text data (e.g., UTF-8, UTF-16LE, or UTF-16BE).
  private final Charset charset;

  // Constructor initializes the StorageEngine with a BackingFile instance.
  public StorageEngine(BackingFile file) {
    this.file = file; // Store the reference to the backing file.
    Header header = Header.read(file); // Read the header to get metadata about the database.
    this.pageSize = header.pageSize; // Set the page size based on header information.
    this.charset = switch (header.encoding) { // Determine character set based on encoding type in header.
      case Utf16be -> StandardCharsets.UTF_16BE;
      case Utf16le -> StandardCharsets.UTF_16LE;
      case Utf8 -> StandardCharsets.UTF_8;
    };
  }

  // Returns information about the database, such as page size and number of tables.
  public Map<String, Object> getInfo() {
    return Map.of(
            "database page size", pageSize,
            "number of tables", getTables().size() // Get the count of tables from schema.
    );
  }

  // Creates and returns a Table object representing the antdb_schema,
  // which contains metadata about other tables in the database.
  private Table schema() {
    return new Table(
            this,
            "antdb_schema",
            getPage(1).asTablePage(), // Get the root page for this schema table.
            SCHEMA // Use predefined schema definition.
    );
  }

  // Retrieves all objects (tables and indices) defined in the schema table.
  public List<Map<String, String>> getObjects() {
    ArrayList<Map<String, String>> objects = new ArrayList<>(); // List to hold metadata about objects.
      // Iterate through rows of schema table.
      schema().rows().forEach(r -> {
          HashMap<String, String> object = Stream.of("name", "tbl_name", "type", "rootpage", "sql")
                  // Create a map to hold individual object metadata.
                  .collect(Collectors.toMap(col -> col, col -> "%s".formatted(r.get(col).display()), (a, b) -> b, HashMap::new));
          // Add each relevant column's value to the object map, formatting it for display.
          objects.add(object); // Add object metadata map to the list of objects.
      });
      return objects; // Return list of all objects found in schema.
  }

  // Retrieves all indices defined in the schema table.
  public List<Index> getIndices() {
    ArrayList<Index> indices = new ArrayList<>(); // List to hold index objects.
      // Retrieve corresponding table for this index or throw an error if it doesn't exist.
      // Create a new Index object and add it to the list of indices.
      schema().rows().stream().filter(r -> r.get("type").getString().equals("index")).forEach(r -> {
          String name = r.get("name").getString(); // Get index name from row data.
          String tableName = r.get("tbl_name").getString(); // Get associated table name from row data.
          Table table = getTable(tableName).orElseThrow(
                  () -> new StorageException("index %s: table does not exist: %s".formatted(name, tableName))
          );
          indices.add(new Index(this, name, table, getPage(
                  (int) r.get("rootpage").getInt()).asIndexPage(),
                  r.get("sql").getString()));
      });
    return indices; // Return list of all indices found in schema.
  }

  // Retrieves all tables defined in the schema table.
  public List<Table> getTables() {
      // Create a new Table object and add it to the list of tables if it is defined as a table type in schema.
      return schema().rows().stream().filter(r -> r.get("type").getString().equals("table")).map(r -> new Table(this, r.get("name").getString(), getPage(
              (int) r.get("rootpage").getInt()).asTablePage(),
              r.get("sql").getString())).collect(Collectors.toCollection(ArrayList::new)); // Return list of all tables found in schema.
  }

  // Helper method to retrieve a specific table by its name if it exists.
  private Optional<Table> getTable(String name) {
    // Stream through existing tables and find one matching the specified name.
    return getTables().stream().filter(t -> t.name().equals(name)).findFirst();
  }

  private enum TextEncoding {Utf8, Utf16le, Utf16be}

  // A record representing header information read from the backing file. Contains page size,
  // total number of pages, and encoding type used in this database instance.
  private record Header(int pageSize, int pageCount, TextEncoding encoding) {
    static Header read(BackingFile file) {
      ByteBuffer bytes = ByteBuffer.allocate(100).order(ByteOrder.BIG_ENDIAN);
      if (file.seek(0).read(bytes) != 100) {
        throw new StorageException("invalid header: must contain 100 bytes");
        // Ensure that exactly 100 bytes are read for valid header structure
      }
      int pageSize = Short.toUnsignedInt(bytes.position(16).getShort());
      int pageCount = bytes.position(28).getInt();
      int encoding = bytes.position(56).getInt();

      TextEncoding textEncoding = switch (encoding) {
        case 1 -> TextEncoding.Utf8;
        case 2 -> TextEncoding.Utf16le;
        case 3 -> TextEncoding.Utf16be;
        // Handle invalid encoding values with an exception
        default -> throw new StorageException("bad encoding: %d".formatted(encoding));
      };
      // Return a new Header instance with parsed values
      return new Header(pageSize, pageCount, textEncoding);
    }
  }

  // Retrieves a specific page from storage based on its page number.
  Page<?> getPage(int pageNumber) {
    ByteBuffer page = ByteBuffer.allocate(pageSize).order(ByteOrder.BIG_ENDIAN);
    long offset = (long) (pageNumber - 1) * pageSize;
    int read = file.seek(offset).read(page);
    if (read != page.capacity()) {
      // Ensure that we read exactly as much data as expected into our buffer
      throw new StorageException("bad page size: want %d, got %d".formatted(page.capacity(), read));
    }
    return Page.from(page, pageNumber == 1 ? 100 : 0, charset);
    // Use factory method to create appropriate Page instance based on buffer content
  }
}