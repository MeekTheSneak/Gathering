package dev.gathering.core.story;

import java.util.Locale;

/**
 * The ways a card can come to somebody that are worth remembering.
 *
 * <p>Deliberately short, and it will stay short. Most cards arrive in ways nobody would tell
 * anybody about - conjured by an operator, taken out of a shared box, handed over by a command
 * - and a card that remembered those would be every card, which is the same as no card
 * remembering anything. What is on this list is where the changing of hands <em>is</em> the
 * story: somebody opened it, somebody won it off you, somebody traded for it.
 *
 * <p><b>A draft pick is not here yet, and the reason is worth writing down.</b> A drafted pool
 * is handed out as a deck rather than as cards, so at the moment of picking there is no card
 * to write on - the identities live in a component until somebody takes one out. Adding it
 * means the deck carrying the story until then, which is a bigger change than this, and it is
 * the obvious next chapter to add.
 *
 * <p>An enum rather than a string because it crosses the wire and comes back off disk, and a
 * free string there is a way to write anything into somebody's card. Adding one later is safe:
 * on disk a chapter is named and an unknown name is dropped, and on the wire it is an ordinal
 * appended to the end.
 *
 * <p>Pure.
 */
public enum HowItCame {

    /** Pulled out of a booster as the card it was opened for. */
    PULLED,

    /** Won in an ante pot, off whoever staked it. */
    WON,

    /** Taken in a trade, from whoever put it up. */
    TRADED;

    /** The name this goes by in a file and on the wire. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** What a player reads. Translated, so every word of a card's story is editable. */
    public String translationKey() {
        return "story.gathering." + id();
    }

    /** Every one, once. {@code values()} clones its array and this is read while drawing. */
    private static final HowItCame[] ALL = values();

    public static HowItCame[] all() {
        return ALL;
    }

    /**
     * The one with this name, or null.
     *
     * <p>Null rather than an exception: this reads a word off a disk written by some other
     * version of the mod, and a chapter nobody recognizes is a chapter to drop rather than a
     * collection that will not load.
     */
    public static HowItCame named(String id) {
        if (id != null) {
            String wanted = id.trim().toLowerCase(Locale.ROOT);
            for (HowItCame how : ALL) {
                if (how.id().equals(wanted)) {
                    return how;
                }
            }
        }
        return null;
    }

    /** Whether this way of coming names somebody it came from. A pack has no previous owner. */
    public boolean hasSomebodyBefore() {
        return this == WON || this == TRADED;
    }
}
