package dev.gathering.core.story;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a card has been.
 * <p>Most cards in this mod are copies of a printing and nothing else - that is what makes a
 * collection of ten thousand fit in a few hundred entries, and it is the right answer for the
 * fortieth Forest. But some cards are not copies. A card won off somebody in an ante game is
 * not the same object as one fished out of the sea, and until something writes that down the
 * mod knows the difference and never says it.
 * <p>So a card gains a story only when something worth remembering happens to it - see
 * {@link HowItCame}, which is four things and will stay four. A card with no story is a card
 * with nothing to say, costs nothing, and stacks with its fellows as it always did.
 * <p><b>Bounded, and it loses the middle rather than the ends.</b> A card that changed hands a
 * hundred times would otherwise be a hundred lines nobody reads and a hundred entries nobody
 * can store. What matters about a well-traveled card is where it started and how it got to
 * you; the twelve owners in between are a number. So an overlong story keeps its first chapter
 * and its most recent ones and says how many it dropped.
 * <p>Pure, and immutable: adding a chapter hands back a new story, which is what lets one be
 * held on an item without anything being able to edit it in place.
 */
public record CardStory(List<Chapter> chapters, int forgotten) {

    /**
     * How many chapters a card keeps.
     * <p>Enough for a card with a real history - opened, won, traded on, won again - and few
     * enough that the longest possible story is still a paragraph and still a few dozen bytes
     * on an item that a hundred of might be in one deck.
     */
    public static final int MOST = 8;

    /** How long a name may be. Minecraft's own limit, so any real player's name fits. */
    public static final int LONGEST_NAME = 16;

    public static final CardStory NONE = new CardStory(List.of(), 0);

    /**
     * One thing that happened to a card.
     *
     * @param how     the kind of thing it was
     * @param who     whose hands it came into, as a name rather than an id: this is read years
     *     later, and a player who has left is still the player who won it
     * @param from    whose hands it came out of, empty where there were none - a booster has
     *     no previous owner
     * @param what    what it happened at: a set code for a pull, a format for a draft, blank
     *     where there is nothing to say
     * @param day     the day it happened, as {@code YYYY-MM-DD}, or blank
     */
    public record Chapter(HowItCame how, String who, String from, String what, String day) {

        public Chapter {
            how = how == null ? HowItCame.PULLED : how;
            who = trim(who);
            from = trim(from);
            what = trim(what);
            day = trim(day);
        }

        private static String trim(String said) {
            if (said == null) {
                return "";
            }
            String kept = said.trim();
            return kept.length() > LONGEST_NAME ? kept.substring(0, LONGEST_NAME) : kept;
        }
    }

    public CardStory {
        List<Chapter> kept = new ArrayList<>();
        if (chapters != null) {
            for (Chapter chapter : chapters) {
                if (chapter != null) {
                    kept.add(chapter);
                }
            }
        }
        forgotten = Math.max(0, forgotten);
        chapters = List.copyOf(kept);
    }

    /** Whether anything worth saying has happened to this card. */
    public boolean isEmpty() {
        return chapters.isEmpty();
    }

    /** How it first came into anybody's hands, which is where a card is from. */
    public Chapter beginning() {
        return chapters.isEmpty() ? null : chapters.get(0);
    }

    /** And how it came into these ones, which is usually the interesting half. */
    public Chapter latest() {
        return chapters.isEmpty() ? null : chapters.get(chapters.size() - 1);
    }

    /** Whether any of this card's history had to be dropped to keep it a paragraph. */
    public boolean hasGaps() {
        return forgotten > 0;
    }

    /**
     * The same card, with one more thing having happened to it.
     * <p>Over the limit it drops the second chapter rather than the first, so where a card
     * came from survives however far it travels afterwards - that is the half nothing else
     * records, and the half somebody is actually curious about.
     */
    public CardStory and(Chapter chapter) {
        if (chapter == null) {
            return this;
        }
        List<Chapter> more = new ArrayList<>(chapters);
        more.add(chapter);
        int dropped = forgotten;
        while (more.size() > MOST) {
            more.remove(1);
            dropped++;
        }
        return new CardStory(more, dropped);
    }

    /** A card that has just come to somebody for the first time. */
    public static CardStory begunWith(Chapter chapter) {
        return NONE.and(chapter);
    }
}
