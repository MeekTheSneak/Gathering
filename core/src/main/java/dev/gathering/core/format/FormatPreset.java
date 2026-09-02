package dev.gathering.core.format;

/**
 * A format, as data.
 * <p>Nine fields and no code. Adding a format later is a table entry in
 * {@link FormatPresets}, not a new branch anywhere, which is the whole reason this is a
 * record and the validator is one method.
 * <p>None of this is ever consulted during play. Deck validation is the sole permitted
 * referee in the entire mod, it runs before a formatted game begins, and it stops the moment
 * the session starts. That fence is permanent.
 *
 * @param legalitiesKey     the key into Scryfall's legalities map - "commander", "modern"
 * @param maximumDeckSize   -1 for no maximum; Commander is exactly 100 either way
 * @param copyLimit         4 for most formats, 1 for singleton ones
 * @param maximumSideboard  0 where the format has no sideboard
 */
public record FormatPreset(
        String id,
        String displayName,
        String legalitiesKey,
        int minimumDeckSize,
        int maximumDeckSize,
        int copyLimit,
        int startingLife,
        CommanderRules commanderRules,
        int maximumSideboard) {

    public FormatPreset {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A format preset needs an id");
        }
        if (copyLimit < 1) {
            throw new IllegalArgumentException("A copy limit of " + copyLimit + " would allow no deck at all");
        }
    }

    public boolean isSingleton() {
        return copyLimit == 1;
    }

    public boolean hasDeckSizeMaximum() {
        return maximumDeckSize > 0;
    }

    public boolean hasSideboard() {
        return maximumSideboard > 0;
    }

    /** Commander formats check that every card fits inside the commander's colors. */
    public boolean checksColorIdentity() {
        return commanderRules.inUse();
    }

    /**
     * Whether a board in this format has a command zone on it.
     * <p>The same question as the one above and a different reason for asking: that one is
     * about what a decklist is allowed to contain and this one is about whether a box gets
     * drawn on the mat. Named separately so the drawing does not read as a rules check, which
     * is a thing this mod is careful never to do during play.
     */
    public boolean hasCommandZone() {
        return commanderRules.inUse();
    }
}
