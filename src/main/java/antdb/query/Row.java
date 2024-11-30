package antdb.query;

import java.util.List;

// The Row class represents a single record in a database table.
// It encapsulates the values of the columns for that record.
public record Row(List<Value> columns) {
    // The 'columns' field holds a list of Value objects, each representing a column's data.
    // The order of the values in this list corresponds to the order of columns defined in the table schema.

    // Example usage:
    // If a table has columns: id (INTEGER), username (TEXT), and age (INTEGER),
    // a Row object might contain: [new IntValue(1), new StringValue("john_doe"), new IntValue(30)]
}