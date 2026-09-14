package dev.gathering.core.tournament;

import java.util.UUID;

/**
 * Somebody taking part in a tournament.
 *
 * @param id          who
 * @param name        what they are called on standings
 * @param seed        their private rating when they registered; used to pair round one and to
 *                    order a cut, and never shown to them
 * @param droppedAfter the last round they played, or -1 while still in; a dropped player keeps
 *                    their record in the standings and is not paired again
 */
public record Entrant(UUID id, String name, double seed, int droppedAfter) {

    public Entrant {
        if (id == null) {
            throw new IllegalArgumentException("An entrant needs an id");
        }
        name = name == null || name.isBlank() ? "Player" : name;
    }

    public static Entrant registering(UUID id, String name, double seed) {
        return new Entrant(id, name, seed, -1);
    }

    public boolean isDropped() {
        return droppedAfter >= 0;
    }

    public Entrant droppedAfter(int round) {
        return new Entrant(id, name, seed, round);
    }
}
