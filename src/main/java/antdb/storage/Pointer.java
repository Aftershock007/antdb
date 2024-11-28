package antdb.storage;

import java.util.Optional;

public record Pointer<Key>(Endpoint<Key> left, Endpoint<Key> right,
                           int pageNumber) {
  public sealed interface Endpoint<Key> permits Unbounded,
      Bounded {
    default Optional<Key> get() {
      return switch (this) {
        case Bounded<Key> e -> Optional.of(e.endpoint());
        case Unbounded<Key> ignored -> Optional.empty();
      };
    }
  }
  public record Unbounded<Key>() implements Endpoint<Key> {}
  public record Bounded<Key>(Key endpoint) implements Endpoint<Key> {}
}
