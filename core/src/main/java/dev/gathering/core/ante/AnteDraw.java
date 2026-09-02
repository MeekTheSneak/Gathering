package dev.gathering.core.ante;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Which cards a library gives up to the pot.
 * <p>Taken off the top of a library that has just been shuffled, which is what makes an ante
 * card random without anything here having to be: the shuffle already happened, with the
 * session's own seed, and a second source of randomness would be a second thing to get wrong.
 * <p>The exclusion list is applied by passing cards over rather than by re-rolling. Those are
 * the same thing on a shuffled deck and only one of them is checkable: "keep drawing until
 * you get one you are allowed to stake" is a loop that can run forever on a deck of nothing
 * but basics, and "walk down the deck and take the first ones you may" cannot.
 */
public final class AnteDraw {

    /** What a library handed over, and what it kept back. */
    public record Taken(List<CardIdentity> staked, List<CardIdentity> passedOver) {

        public Taken {
            staked = staked == null ? List.of() : List.copyOf(staked);
            passedOver = passedOver == null ? List.of() : List.copyOf(passedOver);
        }

        public boolean isEmpty() {
            return staked.isEmpty();
        }

        /** Whether the library could not cover the stake, so the table cannot play for keeps. */
        public boolean isShort(int wanted) {
            return staked.size() < wanted;
        }
    }

    /** What is known about a card, which the pure layer cannot look up for itself. */
    @FunctionalInterface
    public interface Cards {

        Optional<CardMetadata> of(CardIdentity card);
    }

    private AnteDraw() {
    }

    /**
     * Takes this many cards off the top, skipping what the server protects.
     *
     * @param library  the seat's library, top first, already shuffled
     * @param howMany  what each player is staking
     * @param excluded what may not be staked
     * @param cards    where a card's details come from; a card it cannot answer for is
     *                 protected, because the only safe answer about somebody's property is no
     * @return what was taken and what was walked past to get it. The passed-over cards stay
     *         exactly where they were: this decides which cards leave, and nothing here
     *         reorders a library, because a deck reordered on the way into a game is a deck
     *         whose shuffle happened twice.
     */
    public static Taken from(
            List<CardIdentity> library, int howMany, AnteExclusions excluded, Cards cards) {
        if (library == null || library.isEmpty() || howMany <= 0) {
            return new Taken(List.of(), List.of());
        }
        AnteExclusions rules = excluded == null ? AnteExclusions.NOTHING : excluded;
        List<CardIdentity> staked = new ArrayList<>(Math.min(howMany, library.size()));
        List<CardIdentity> passedOver = new ArrayList<>();

        for (CardIdentity card : library) {
            if (staked.size() >= howMany) {
                break;
            }
            // A server protecting nothing needs nothing looked up. Without this the
            // unknown-is-protected rule - which is right when there is a list to check
            // against - would skip cards on a server that had asked for no list at all.
            if (rules.isEmpty()) {
                staked.add(card);
                continue;
            }
            CardMetadata known = cards == null
                    ? null
                    : cards.of(card).orElse(null);
            if (rules.protects(known, card.foil())) {
                passedOver.add(card);
                continue;
            }
            staked.add(card);
        }
        return new Taken(staked, passedOver);
    }
}
