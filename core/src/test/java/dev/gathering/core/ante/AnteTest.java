package dev.gathering.core.ante;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.game.SeatId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class AnteTest {

    private static final Map<UUID, CardMetadata> KNOWN = new HashMap<>();

    // ------------------------------------------------------------------ exclusions

    @Test
    void theShippedDefaultProtectsBasicLandsAndNothingElse() {
        AnteExclusions.Reading read = AnteExclusions.of(List.of("basic lands"));
        assertThat(read.notes()).isEmpty();
        assertThat(read.exclusions().protects(card("Forest", "Basic Land - Forest",
                Rarity.COMMON), false)).isTrue();
        assertThat(read.exclusions().protects(card("Wastes", "Land", Rarity.COMMON), false))
                .isFalse();
        assertThat(read.exclusions().protects(card("Black Lotus", "Artifact", Rarity.RARE),
                false)).isFalse();
    }

    @Test
    void caseAndSpacingAreTheConfigAuthorsBusinessRatherThanTheRules() {
        AnteExclusions.Reading read = AnteExclusions.of(List.of("  Basic Lands  "));
        assertThat(read.notes()).isEmpty();
        assertThat(read.exclusions().categories()).containsExactly(AnteExclusions.BASIC_LANDS);
    }

    @Test
    void aWordTheListDoesNotKnowProtectsNothingAndSaysSo() {
        AnteExclusions.Reading read = AnteExclusions.of(List.of("planeswalkers", "lands"));
        assertThat(read.notes()).hasSize(1);
        assertThat(read.notes().get(0)).contains("planeswalkers");
        // And the one it did understand still works: a typo does not turn the list off.
        assertThat(read.exclusions().protects(card("Island", "Basic Land - Island",
                Rarity.COMMON), false)).isTrue();
    }

    @Test
    void aCardNothingIsKnownAboutIsProtected() {
        AnteExclusions only = AnteExclusions.of(List.of("basic lands")).exclusions();
        assertThat(only.protects(null, false)).isTrue();
    }

    @Test
    void raresCoverMythicsBecauseAMythicIsARareYouCareAboutMore() {
        AnteExclusions rares = AnteExclusions.of(List.of("rares")).exclusions();
        assertThat(rares.protects(card("A", "Creature", Rarity.RARE), false)).isTrue();
        assertThat(rares.protects(card("B", "Creature", Rarity.MYTHIC), false)).isTrue();
        assertThat(rares.protects(card("C", "Creature", Rarity.UNCOMMON), false)).isFalse();

        AnteExclusions mythics = AnteExclusions.of(List.of("mythics")).exclusions();
        assertThat(mythics.protects(card("A", "Creature", Rarity.RARE), false)).isFalse();
        assertThat(mythics.protects(card("B", "Creature", Rarity.MYTHIC), false)).isTrue();
    }

    @Test
    void foilsAreProtectedByTheCardsFinishRatherThanByItsPrinting() {
        AnteExclusions foils = AnteExclusions.of(List.of("foils")).exclusions();
        CardMetadata bear = card("Grizzly Bears", "Creature - Bear", Rarity.COMMON);
        assertThat(foils.protects(bear, true)).isTrue();
        assertThat(foils.protects(bear, false)).isFalse();
    }

    // ------------------------------------------------------------------ the draw

    @Test
    void aStakeComesOffTheTopOfTheShuffledDeck() {
        List<CardIdentity> library = List.of(id("a"), id("b"), id("c"));
        AnteDraw.Taken taken = AnteDraw.from(library, 2, AnteExclusions.NOTHING, lookup());
        assertThat(taken.staked()).containsExactly(id("a"), id("b"));
        assertThat(taken.passedOver()).isEmpty();
    }

    @Test
    void aProtectedCardIsWalkedPastRatherThanRerolled() {
        CardIdentity forest = printing("Forest", "Basic Land - Forest", Rarity.COMMON);
        CardIdentity bolt = printing("Lightning Bolt", "Instant", Rarity.COMMON);
        List<CardIdentity> library = List.of(forest, forest, bolt);

        AnteDraw.Taken taken = AnteDraw.from(
                library, 1, AnteExclusions.of(List.of("basic lands")).exclusions(), lookup());

        assertThat(taken.staked()).containsExactly(bolt);
        assertThat(taken.passedOver()).containsExactly(forest, forest);
    }

    @Test
    void aDeckOfNothingButProtectedCardsStakesNothingRatherThanLooping() {
        CardIdentity forest = printing("Forest", "Basic Land - Forest", Rarity.COMMON);
        AnteDraw.Taken taken = AnteDraw.from(
                List.of(forest, forest, forest), 1,
                AnteExclusions.of(List.of("basic lands")).exclusions(), lookup());

        assertThat(taken.isEmpty()).isTrue();
        assertThat(taken.isShort(1)).isTrue();
    }

    @Test
    void aLibraryTooSmallToCoverTheStakeSaysSo() {
        AnteDraw.Taken taken =
                AnteDraw.from(List.of(id("a")), 3, AnteExclusions.NOTHING, lookup());
        assertThat(taken.staked()).hasSize(1);
        assertThat(taken.isShort(3)).isTrue();
    }

    @Test
    void aServerProtectingNothingLooksNothingUp() {
        // The unknown-is-protected rule is right when there is a list to check against. With
        // no list, it would have skipped every card on a server that asked for no list at all.
        List<CardIdentity> library = List.of(id("unknown-1"), id("unknown-2"));
        AnteDraw.Taken taken = AnteDraw.from(
                library, 2, AnteExclusions.NOTHING, card -> Optional.empty());
        assertThat(taken.staked()).hasSize(2);
    }

    @Test
    void nothingIsStakedWhenTheStakeIsNothing() {
        assertThat(AnteDraw.from(List.of(id("a")), 0, AnteExclusions.NOTHING, lookup())
                .isEmpty()).isTrue();
        assertThat(AnteDraw.from(List.of(), 2, AnteExclusions.NOTHING, lookup())
                .isEmpty()).isTrue();
    }

    // ------------------------------------------------------------------ the pot

    @Test
    void aPotPaysOutEverythingToTheWinner() {
        AntePot pot = AntePot.EMPTY
                .with(new SeatId(0), List.of(id("a")))
                .with(new SeatId(1), List.of(id("b"), id("c")));

        AntePot.Payout paid = pot.toWinner(new SeatId(1));
        assertThat(paid.forSeat(new SeatId(1))).containsExactly(id("a"), id("b"), id("c"));
        assertThat(paid.forSeat(new SeatId(0))).isEmpty();
    }

    @Test
    void aVoidedSessionGivesEveryCardBackToWhoeverPutItIn() {
        AntePot pot = AntePot.EMPTY
                .with(new SeatId(0), List.of(id("a")))
                .with(new SeatId(1), List.of(id("b"), id("c")));

        AntePot.Payout back = pot.backToOwners();
        assertThat(back.forSeat(new SeatId(0))).containsExactly(id("a"));
        assertThat(back.forSeat(new SeatId(1))).containsExactly(id("b"), id("c"));
    }

    @Test
    void aPotPaidToNobodyStaysWithItsOwnersRatherThanVanishing() {
        AntePot pot = AntePot.EMPTY.with(new SeatId(0), List.of(id("a")));
        assertThat(pot.toWinner(null).forSeat(new SeatId(0))).containsExactly(id("a"));
    }

    @Test
    void stakingTwiceAddsToTheSameSeatsPileRatherThanReplacingIt() {
        AntePot pot = AntePot.EMPTY
                .with(new SeatId(0), List.of(id("a")))
                .with(new SeatId(0), List.of(id("b")));
        assertThat(pot.stakeOf(new SeatId(0))).containsExactly(id("a"), id("b"));
        assertThat(pot.size()).isEqualTo(2);
    }

    /**
     * The one that matters. However a pot resolves, the cards that come out of it are exactly
     * the cards that went in - no card created, none destroyed. These are somebody's actual
     * property, and an arithmetic slip here is a card that stops existing.
     */
    @Property
    void aPotNeverCreatesOrDestroysACard(@ForAll("pots") AntePot pot, @ForAll int winner) {
        List<CardIdentity> wentIn = sorted(pot.everything());

        assertThat(sorted(pot.toWinner(new SeatId(Math.floorMod(winner, 4))).everything()))
                .isEqualTo(wentIn);
        assertThat(sorted(pot.backToOwners().everything())).isEqualTo(wentIn);
        assertThat(sorted(pot.toWinner(null).everything())).isEqualTo(wentIn);
    }

    /** And a draw never invents one either: what was taken plus what was left is the library. */
    @Property
    void aDrawOnlyEverMovesCardsThatWereInTheLibrary(
            @ForAll("libraries") List<CardIdentity> library, @ForAll int howMany) {
        int wanted = Math.floorMod(howMany, 5);
        AnteDraw.Taken taken = AnteDraw.from(
                library, wanted, AnteExclusions.of(List.of("basic lands")).exclusions(), lookup());

        assertThat(taken.staked().size()).isLessThanOrEqualTo(wanted);
        assertThat(library).containsAll(taken.staked());
        assertThat(library).containsAll(taken.passedOver());
        // Nothing is both staked and left behind.
        assertThat(taken.staked().size() + taken.passedOver().size())
                .isLessThanOrEqualTo(library.size());
    }

    @Provide
    Arbitrary<AntePot> pots() {
        return Arbitraries.integers().between(0, 3).list().ofMaxSize(6).map(seats -> {
            AntePot pot = AntePot.EMPTY;
            int card = 0;
            for (int seat : seats) {
                pot = pot.with(new SeatId(seat), List.of(id("c" + card++)));
            }
            return pot;
        });
    }

    @Provide
    Arbitrary<List<CardIdentity>> libraries() {
        return Arbitraries.of(
                        printing("Forest", "Basic Land - Forest", Rarity.COMMON),
                        printing("Lightning Bolt", "Instant", Rarity.COMMON),
                        printing("Black Lotus", "Artifact", Rarity.RARE))
                .list().ofMaxSize(12);
    }

    // ------------------------------------------------------------------ bits

    private static List<CardIdentity> sorted(List<CardIdentity> cards) {
        List<CardIdentity> copy = new ArrayList<>(cards);
        copy.sort(java.util.Comparator.comparing(card -> String.valueOf(card.scryfallId())));
        return copy;
    }

    private static AnteDraw.Cards lookup() {
        return card -> Optional.ofNullable(KNOWN.get(card.scryfallId()));
    }

    private static CardIdentity id(String name) {
        return CardIdentity.ofPrinting(
                UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                false);
    }

    /** A card the lookup can answer for, so exclusions have something to read. */
    private static CardIdentity printing(String name, String types, Rarity rarity) {
        CardIdentity identity = id(name);
        KNOWN.put(identity.scryfallId(), card(name, types, rarity));
        return identity;
    }

    private static CardMetadata card(String name, String typeLine, Rarity rarity) {
        UUID id = UUID.nameUUIDFromBytes(
                name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new CardMetadata(
                id, id, name, "", 0.0, typeLine, "", java.util.Set.of(), java.util.Set.of(),
                List.of(), "normal", "tst", "Test Set", "1", rarity,
                false, true, true, false, false, List.of("paper"), Map.of(), Map.of(), "");
    }
}
