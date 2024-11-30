package antdb.storage;

import java.util.Optional;

// The Pointer class represents a pointer structure used in the B-tree implementation of the index.
// It holds references to child nodes (left and right) and the page number of the current node.
public record Pointer<Key>(Endpoint<Key> left, Endpoint<Key> right, int pageNumber) {
  // The Endpoint interface represents the endpoints of a pointer, which can be either bounded or unbounded.
  public sealed interface Endpoint<Key> permits Unbounded, Bounded {
    // Retrieves the key value from the endpoint if it is bounded.
    default Optional<Key> get() {
      return switch (this) {
        case Bounded<Key> e -> Optional.of(e.endpoint()); // Return the endpoint's key if bounded.
        case Unbounded<Key> ignored -> Optional.empty(); // Return empty if unbounded (no key).
      };
    }
  }

  // Represents an unbounded endpoint, which does not hold a specific key value.
  public record Unbounded<Key>() implements Endpoint<Key> {}

  // Represents a bounded endpoint, which holds a specific key value.
  public record Bounded<Key>(Key endpoint) implements Endpoint<Key> {}
}