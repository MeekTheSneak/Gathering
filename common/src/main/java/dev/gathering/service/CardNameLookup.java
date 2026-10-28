package dev.gathering.service;

import dev.gathering.item.CardComponent;
import java.util.Optional;

/**
 * Answers "what is this card called?" from whatever metadata the current side happens to
 * hold.
 * <p>A card item is a pointer, so its display name is not stored on it. The server resolves
 * names from its own cache; a client resolves them from the metadata it has been sent and
 * the art it has fetched. Neither side is authoritative for the other, and a client that has
 * not been told about a card simply does not know its name - which is the correct behavior
 * for a mod whose whole security property is that clients only learn what they are entitled
 * to.
 */
@FunctionalInterface
public interface CardNameLookup {

    /** Empty when this side does not (yet, or ever) know. */
    Optional<String> nameOf(CardComponent card);

    /** The honest default: knows nothing until something binds a real lookup. */
    CardNameLookup UNKNOWN = card -> Optional.empty();

    final class Binding {

        private static volatile CardNameLookup current = UNKNOWN;

        private Binding() {
        }

        public static void bind(CardNameLookup lookup) {
            current = java.util.Objects.requireNonNull(lookup, "lookup");
        }

        public static CardNameLookup current() {
            return current;
        }
    }
}
