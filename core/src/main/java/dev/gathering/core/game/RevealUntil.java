package dev.gathering.core.game;

import dev.gathering.core.card.CardMetadata;
import java.util.List;
import java.util.function.Predicate;

/**
 * How far down a library you turn cards before you find what you are looking for.
 * <p>Cascade turns cards over until one costs less than the spell that cascaded; "reveal until
 * type" turns them over until one is a creature, or a land, or whatever was asked for. Two
 * names for one act, so one rule with the question passed in.
 * <p>The answer is a <em>count</em>, deliberately. Turning the top four cards of a library face
 * up is something the table already has a word for, and the whole apparatus that goes with it -
 * an event, a fold, a visibility decision about who is entitled to see them. A cascade that
 * invented its own way of showing cards would be a second answer to a question already
 * answered, and the interesting half of it - which is only "how many" - is this.
 * <p>No rules enforcement. Nothing here casts the card it stops on, or moves anything, or knows
 * what cascade means. It counts, everybody sees the same cards turned over, and what happens
 * next is what the players say happens next - the same as it is across a real table.
 * <p>Pure: no Minecraft, no session, no network, so the arithmetic is tested rather than
 * played.
 */
public final class RevealUntil {

    /**
     * The most cards one of these will ever turn over.
     * <p>A cascade off a one-drop into a deck with no cheaper card would otherwise turn the
     * whole library face up, which is not a reveal, it is showing everybody your deck. A
     * hundred is past any real answer and well short of that.
     */
    public static final int MOST_TO_TURN_OVER = 100;

    private RevealUntil() {
    }

    /**
     * How many cards to turn over, counting the one that stopped it.
     * <p>Including the card that matched, because that is the one being looked for: a cascade
     * that revealed everything <em>above</em> the hit would leave the hit face down, which is
     * the one card everyone at the table is waiting to see.
     * <p>Zero when nothing matches, which is a real answer and not a failure: a deck with
     * nothing cheaper in it reveals nothing and says so, rather than turning itself inside out
     * looking. The caller decides what to tell the player about that.
     *
     * @param fromTheTop the library, top card first; a null entry is a card whose name this
     *     server has never looked up, and never matches - see below
     */
    public static int howFarDown(List<CardMetadata> fromTheTop, Predicate<CardMetadata> stopAt) {
        if (fromTheTop == null || stopAt == null) {
            return 0;
        }
        int looked = Math.min(fromTheTop.size(), MOST_TO_TURN_OVER);
        for (int down = 0; down < looked; down++) {
            CardMetadata card = fromTheTop.get(down);
            // A card nobody has looked up cannot be judged, and guessing would be worse than
            // not stopping: stopping on it would show a card that might not match, and the
            // player would have no way to tell which it was. It is still turned over on the
            // way past, so nothing is hidden - the count simply carries on.
            if (card != null && stopAt.test(card)) {
                return down + 1;
            }
        }
        return 0;
    }

    /**
     * Cascade's question: the first <em>nonland</em> card that costs less than this.
     * <p>Nonland is not a refinement, it is most of the rule. A land's mana value is zero,
     * so "the first card that costs less" is the first land - and a deck is a third lands,
     * which made this stop wrong on nearly every real cascade. What cascade actually says is
     * that lands are turned over and passed by, and the spell you get is a spell.
     */
    public static Predicate<CardMetadata> cheaperThan(int manaValue) {
        return card -> !card.isLand() && card.cmc() < manaValue;
    }


    /**
     * The other one: the first card whose type line says this.
     * <p>Matched loosely and without case, because the player is typing it. "creature" finds
     * "Legendary Creature - Elf Druid", and somebody who types "elf" finds it too - a type
     * line is the whole line on the card, and the useful question is nearly always "is this
     * word on it".
     */
    public static Predicate<CardMetadata> ofType(String type) {
        if (type == null || type.isBlank()) {
            return card -> false;
        }
        String wanted = type.strip().toLowerCase(java.util.Locale.ROOT);
        return card -> card.typeLine() != null
                && card.typeLine().toLowerCase(java.util.Locale.ROOT).contains(wanted);
    }
}
