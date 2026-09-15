package dev.gathering.core.match;

import dev.gathering.core.format.FormatPreset;
import dev.gathering.core.format.FormatPresets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a table is playing: the format, how long the match is, which game of it this is, and
 * anything about it somebody sitting down would want to know before they do.
 * <p>The board never said. A player walking up to a table saw cards and life totals and had to
 * ask out loud whether it was Modern or Commander, whether it was one game or three - and nothing
 * at all told them the game was being played for keeps, which is the one thing that changes what
 * sitting down costs. Charta, another card mod, flags a game whose options differ from the usual
 * on its own screen; this is that, for the settings this mod has.
 * <p>Nothing here is enforced - the mod enforces no rules - and nothing is hidden: every part of
 * it is something the table agreed out loud.
 *
 * @param formatId   the preset's id, or empty for a game with no format
 * @param bestOf     how many games the match is
 * @param gameNumber which game of it is being played, from one
 * @param freePlay   started with no format named
 * @param forKeeps   the winner keeps what is in the pot
 * @param practice   a practice table, where nothing is kept
 * @param eventTable the tournament table number this is, or zero
 */
public record TableTerms(
        String formatId, int bestOf, int gameNumber, boolean freePlay, boolean forKeeps, boolean practice,
        int eventTable) {

    /** Something about a table worth saying, most consequential first. */
    public enum Note {
        /** The winner keeps what is in the pot. */
        FOR_KEEPS,
        /** No format: nobody's deck is held to one. */
        FREE_PLAY,
        /** A match of a length other than the format's usual one. */
        UNUSUAL_LENGTH,
        /** A tournament table. */
        EVENT,
        /** A practice table: nothing is kept. */
        PRACTICE
    }

    public TableTerms {
        formatId = formatId == null ? "" : formatId;
        bestOf = Math.max(1, bestOf);
        gameNumber = Math.max(1, gameNumber);
        eventTable = Math.max(0, eventTable);
    }

    /** The format, when there is one this game knows. */
    public Optional<FormatPreset> format() {
        return freePlay ? Optional.empty() : FormatPresets.byId(formatId);
    }

    /**
     * How long a match of this format usually is: three games where there is a sideboard to bring
     * in between them, one where there is not. The same choice the table's setup offers first.
     */
    public int usualLength() {
        return format().map(preset -> preset.hasSideboard() ? 3 : 1).orElse(1);
    }

    /** Everything worth saying about this table, most consequential first. */
    public List<Note> notes() {
        List<Note> notes = new ArrayList<>();
        if (forKeeps) {
            notes.add(Note.FOR_KEEPS);
        }
        if (freePlay) {
            notes.add(Note.FREE_PLAY);
        } else if (format().isPresent() && bestOf != usualLength() && eventTable == 0) {
            // An event sets its own length for every table, which is the event's usual.
            notes.add(Note.UNUSUAL_LENGTH);
        }
        if (eventTable > 0) {
            notes.add(Note.EVENT);
        }
        if (practice) {
            notes.add(Note.PRACTICE);
        }
        return List.copyOf(notes);
    }

    /**
     * Whether this table plays by other than the usual terms somebody sitting down would expect:
     * for keeps, with no format, or a match of an unusual length. What the board marks.
     */
    public boolean unusual() {
        return notes().stream().anyMatch(note -> note == Note.FOR_KEEPS || note == Note.FREE_PLAY
                || note == Note.UNUSUAL_LENGTH);
    }
}
