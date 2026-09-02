package dev.gathering.server;

import dev.gathering.core.ante.AnteDraw;
import dev.gathering.core.ante.AnteExclusions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.service.CardDataService;
import dev.gathering.service.ServerSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * Taking a player's stake out of their deck.
 *
 * <p>Drawn before the deck becomes a library, not after, and that is a correctness decision
 * rather than a convenience. Undo works by marking events undone and folding the game again
 * from the beginning, so a stake taken as a game event could be rewound - and the cards would
 * come back into the library while the pot on the table was still holding them. One card, two
 * places. Taking the stake before the game has heard of the deck puts it beyond undo's reach
 * entirely, which is the only version of this that cannot go wrong.
 *
 * <p>Random, and not from the session's seed. The brief's reasoning is that the top of a
 * shuffled deck is random; a copy of the deck shuffled here is random by the same argument
 * and never touches the seed, which is the most sensitive value on the server and must not
 * leave the session that owns it.
 *
 * <p>What the server protects is read from whatever is already in the card cache. A card the
 * cache cannot answer for is protected rather than staked, which is
 * {@link AnteExclusions}'s rule and the right one here: the only safe answer about somebody's
 * property is no when nobody can tell.
 */
public final class Staking {

    /** What a seat is staking, and what is left of its deck. */
    public record Stake(List<CardIdentity> staked, List<CardIdentity> library) {

        public Stake {
            staked = staked == null ? List.of() : List.copyOf(staked);
            library = library == null ? List.of() : List.copyOf(library);
        }

        public boolean isEmpty() {
            return staked.isEmpty();
        }
    }

    private Staking() {
    }

    /** Nothing staked, and the deck exactly as it arrived. */
    public static Stake nothing(List<CardIdentity> deck) {
        return new Stake(List.of(), deck);
    }

    /**
     * The deck the table should hold: the one that was put down, less what the pot now has.
     *
     * <p>The stake is taken out of the <em>library</em> the game is dealt, and that is only
     * half the job. The table also keeps the {@link DeckComponent} itself, so it can hand the
     * whole deck back when the match is over - and a deck handed back with the staked card
     * still in it is a card that exists twice: once in the winner's hands, once back in the
     * loser's deck. Ante is the one feature in this mod whose entire point is that a card
     * really changes owner, so that is the one arithmetic mistake it must not make.
     *
     * <p>Only the mainboard, because only the mainboard was ever staked from - commanders are
     * not in the library to be drawn from, and the sideboard is not in play at all.
     *
     * <p>One copy per staked card, by {@code withoutOne}: a deck holding four of something
     * that staked one must come back holding three, not none.
     */
    public static DeckComponent heldAfter(DeckComponent deck, List<CardIdentity> staked) {
        if (deck == null || staked == null || staked.isEmpty()) {
            return deck;
        }
        DeckComponent left = deck;
        for (CardIdentity card : staked) {
            left = left.withoutOne(DeckComponent.Section.MAINBOARD, CardComponent.of(card))
                    .orElse(left);
        }
        return left;
    }

    /**
     * Draws this seat's stake out of a deck.
     *
     * @param deck   the cards as the deck item holds them, in whatever order it holds them
     * @param random the level's own randomness - anything but the session seed
     * @return what is staked and what is left. The two together are always exactly the deck:
     *         no card is created and none is lost, whatever the exclusion list says.
     */
    public static Stake from(List<CardIdentity> deck, RandomSource random) {
        var settings = ServerSettings.get().ante();
        int wanted = settings.cardsPerPlayer();
        if (deck == null || deck.isEmpty() || wanted <= 0) {
            return nothing(deck);
        }

        AnteExclusions.Reading read = AnteExclusions.of(settings.exclusions());
        AnteDraw.Taken taken = AnteDraw.from(shuffled(deck, random), wanted,
                read.exclusions(), knownCards());
        if (taken.isEmpty()) {
            return nothing(deck);
        }

        // Removed by identity and one at a time, so a deck with four copies of a card that
        // staked one still has three. Removing every match would take the other three with
        // it, which is a player losing four cards to a one-card ante.
        List<CardIdentity> left = new ArrayList<>(deck);
        for (CardIdentity card : taken.staked()) {
            left.remove(card);
        }
        return new Stake(taken.staked(), left);
    }

    /** A copy in a random order. The deck itself is never reordered. */
    private static List<CardIdentity> shuffled(List<CardIdentity> deck, RandomSource random) {
        List<CardIdentity> copy = new ArrayList<>(deck);
        for (int index = copy.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            CardIdentity held = copy.get(index);
            copy.set(index, copy.get(swap));
            copy.set(swap, held);
        }
        return copy;
    }

    /**
     * What the cache already knows, and nothing it would have to go and look up.
     *
     * <p>This runs on the server thread while somebody is putting a deck down, so it cannot
     * wait on a network call. A card that is not in memory reads as unknown, which the
     * exclusion rule treats as protected - so a cold cache stakes less rather than staking
     * something the server said it would not.
     */
    private static AnteDraw.Cards knownCards() {
        CardDataService service = CardDataService.active().orElse(null);
        if (service == null) {
            return card -> java.util.Optional.empty();
        }
        return card -> card.printing().flatMap(service::peek);
    }
}
