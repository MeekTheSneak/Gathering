package dev.gathering.core.game;

import java.util.Objects;
import java.util.UUID;

/**
 * Who is sitting in a seat, as much as the pure core needs to know.
 * <p>The name is carried alongside the id because the event log attributes every action by
 * name and a replay watched a month later should still read "Chris drew a card" rather than
 * a UUID. It is a snapshot at the moment of sitting down, not a live lookup.
 */
public record PlayerRef(UUID id, String name) {

    public PlayerRef {
        Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A player reference needs a name");
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
