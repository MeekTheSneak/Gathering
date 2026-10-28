package dev.gathering.core.trade;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Two people and what they are putting up")
class TradeTableTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BEN = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID NOBODY = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static final CardIdentity BOLT = CardIdentity.ofPrinting(
            UUID.fromString("aaaaaaaa-1111-4111-8111-111111111111"), false);
    private static final CardIdentity RING = CardIdentity.ofPrinting(
            UUID.fromString("bbbbbbbb-2222-4222-8222-222222222222"), false);


    /** Agrees to the table as it stands, which is what somebody looking at it does. */
    private static TradeTable agreeing(TradeTable table, UUID who) {
        return table.agree(who, table.id(), table.revision());
    }

    @Test
    @DisplayName("nothing is struck until both sides have agreed")
    void bothSidesOrNothing() {
        TradeTable table = TradeTable.between(ANA, BEN).putUp(ANA, BOLT, 1);

        assertThat(agreeing(table, ANA).isStruck()).isFalse();
        assertThat(agreeing(agreeing(table, ANA), BEN).isStruck()).isTrue();
    }

    @Test
    @DisplayName("changing an offer takes back both agreements")
    void aChangedOfferUnAgreesEverybody() {
        // The scam every trading system that skipped this has in it: agree, wait for the
        // other side, swap the good card for a worse one, and take theirs.
        TradeTable agreed = agreeing(TradeTable.between(ANA, BEN)
                .putUp(ANA, BOLT, 1)
                .putUp(BEN, RING, 1), BEN);
        assertThat(agreed.hasAgreed(BEN)).isTrue();

        TradeTable swapped = agreed.putUp(ANA, BOLT, 0);

        assertThat(swapped.hasAgreed(BEN))
                .as("what Ben agreed to was a table, and this is a different table")
                .isFalse();
        assertThat(swapped.hasAgreed(ANA)).isFalse();
        assertThat(swapped.isStruck()).isFalse();
    }

    @Test
    @DisplayName("even adding to your own side clears both agreements")
    void generosityIsStillAChange() {
        // It does not matter whether a change is in the other person's favor. They agreed to
        // a table, and anything that is not that table needs agreeing to again.
        TradeTable offered = TradeTable.between(ANA, BEN).putUp(ANA, BOLT, 1);
        TradeTable agreed = agreeing(agreeing(offered, ANA), BEN);
        assertThat(agreed.isStruck()).isTrue();

        // Struck is struck: nothing changes it, which is the other half of the same rule.
        assertThat(agreed.putUp(ANA, RING, 1)).isEqualTo(agreed);

        TradeTable open = agreeing(TradeTable.between(ANA, BEN).putUp(ANA, BOLT, 1), BEN);
        assertThat(open.putUp(ANA, RING, 1).hasAgreed(BEN)).isFalse();
    }

    @Test
    @DisplayName("putting up what is already there changes nothing, and un-agrees nobody")
    void anEmptyChangeIsNoChange() {
        TradeTable agreed = agreeing(TradeTable.between(ANA, BEN).putUp(ANA, BOLT, 2), BEN);

        assertThat(agreed.putUp(ANA, BOLT, 2)).isEqualTo(agreed);
        assertThat(agreed.clearOffer(BEN))
                .as("Ben has nothing up, so clearing it is not a change")
                .isEqualTo(agreed);
    }

    @Test
    @DisplayName("an agreement can be taken back until the other side gives theirs")
    void thinkingAgainWorksUntilItIsStruck() {
        TradeTable one = agreeing(TradeTable.between(ANA, BEN).putUp(ANA, BOLT, 1), ANA);

        assertThat(one.thinkAgain(ANA).hasAgreed(ANA)).isFalse();
        // And once both have agreed there is no window to reach into.
        TradeTable struck = agreeing(one, BEN);
        assertThat(struck.thinkAgain(ANA)).isEqualTo(struck);
    }

    @Test
    @DisplayName("somebody who is not at the table cannot touch it")
    void onlyTheTwoOfThem() {
        TradeTable table = TradeTable.between(ANA, BEN).putUp(ANA, BOLT, 1);

        assertThat(table.putUp(NOBODY, RING, 4)).isEqualTo(table);
        assertThat(agreeing(table, NOBODY)).isEqualTo(table);
        assertThat(table.clearOffer(NOBODY)).isEqualTo(table);
        assertThat(table.seats(NOBODY)).isFalse();
        assertThat(table.offerFrom(NOBODY).isEmpty()).isTrue();
        assertThat(table.across(NOBODY)).isEmpty();
    }

    @Test
    @DisplayName("walking away ends it from anywhere")
    void anybodyMayWalkAway() {
        TradeTable offered = TradeTable.between(ANA, BEN).putUp(ANA, BOLT, 1);
        TradeTable struck = agreeing(agreeing(offered, ANA), BEN);

        TradeTable closed = struck.close();
        assertThat(closed.isStruck()).isFalse();
        assertThat(closed.stage()).isEqualTo(TradeTable.Stage.CLOSED);
        assertThat(closed.putUp(ANA, RING, 1)).isEqualTo(closed);
        assertThat(agreeing(closed, ANA)).isEqualTo(closed);
        assertThat(closed.close()).isEqualTo(closed);
    }

    @Test
    @DisplayName("one side cannot put up more kinds of card than a trade holds")
    void oneSideIsBounded() {
        TradeTable table = TradeTable.between(ANA, BEN);
        for (int card = 0; card < TradeTable.MOST_DISTINCT + 10; card++) {
            table = table.putUp(ANA, CardIdentity.ofPrinting(
                    UUID.fromString(String.format("%08d-1111-4111-8111-111111111111", card)),
                    false), 1);
        }

        assertThat(table.offerFrom(ANA).distinct()).isEqualTo(TradeTable.MOST_DISTINCT);
    }

    /**
     * An agreement is about the terms its sender was looking at.
     * <p>An audit reproduced this end to end: Ben clicks agree on Ana's card; his packet is
     * slow; Ana takes her card back and agrees herself; Ben's packet lands and strikes a
     * trade in which Ana gives nothing and Ben gives his card. Clearing an offer resets both
     * agreement flags, which is why this looked covered - but a flag cannot reach a packet
     * already in flight, and the packet said nothing about which table it had been sent from.
     */
    @Test
    @DisplayName("an agreement sent before the terms changed does not strike the new ones")
    void aSlowAgreementDoesNotStrikeChangedTerms() {
        TradeTable shown = TradeTable.between(ANA, BEN).putUp(ANA, BOLT, 1).putUp(BEN, RING, 1);
        int benWasLookingAt = shown.revision();

        // Ana takes her card back and agrees to the table that leaves.
        TradeTable now = shown.clearOffer(ANA);
        now = now.agree(ANA, now.id(), now.revision());

        // Ben's agreement, sent before any of that, arrives.
        TradeTable after = now.agree(BEN, now.id(), benWasLookingAt);

        assertThat(after.isStruck())
                .describedAs("Ben agreed to a table with Ana's card on it")
                .isFalse();
        assertThat(after.hasAgreed(BEN)).isFalse();
        assertThat(after).isEqualTo(now);

        // Sent again against the terms actually on the table, it goes through.
        assertThat(after.agree(BEN, after.id(), after.revision()).isStruck()).isTrue();
    }

    @Test
    @DisplayName("agreeing does not move the revision on, or nobody could ever agree second")
    void agreeingIsNotAChangeOfTerms() {
        TradeTable shown = TradeTable.between(ANA, BEN).putUp(ANA, BOLT, 1);
        int terms = shown.revision();

        TradeTable one = shown.agree(ANA, shown.id(), terms);

        assertThat(one.revision()).isEqualTo(terms);
        assertThat(one.agree(BEN, one.id(), terms).isStruck()).isTrue();
    }

    @Test
    @DisplayName("an agreement left over from a closed trade cannot strike the next one")
    void anagreementDoesNotOutliveItsOwnTrade() {
        // The revision alone is not enough, and this is why: every table starts at zero, so a
        // packet sent about one trade names a number the next trade between the same two
        // people also has. An audit closed a trade while an agreement was in flight, opened
        // another on different terms - one side offering nothing at all - and struck it with
        // the old packet. Clearing the offers reset both flags, which is exactly why it
        // looked safe; what resetting a flag cannot do is reach a packet already sent.
        TradeTable first = TradeTable.between(ANA, BEN).putUp(ANA, BOLT, 1).putUp(BEN, RING, 1);
        UUID benWasLookingAtTable = first.id();
        int benWasLookingAtTerms = first.revision();
        first.close();

        // Two changes, so it lands on the same revision, and terms nobody agreed to: Ana
        // gives nothing and Ben gives three.
        TradeTable second = TradeTable.between(ANA, BEN).putUp(BEN, RING, 1).putUp(BEN, RING, 3);
        assertThat(second.revision())
                .describedAs("the fixture only means something if the revisions collide")
                .isEqualTo(benWasLookingAtTerms);
        assertThat(second.id()).isNotEqualTo(benWasLookingAtTable);

        TradeTable after = second
                .agree(ANA, second.id(), second.revision())
                .agree(BEN, benWasLookingAtTable, benWasLookingAtTerms);

        assertThat(after.isStruck())
                .describedAs("Ben agreed to a trade that no longer exists")
                .isFalse();
        assertThat(after.hasAgreed(BEN)).isFalse();
        assertThat(after.fromLeft().isEmpty())
                .describedAs("the terms Ben never saw: he gives three and gets nothing")
                .isTrue();
    }

    @Test
    @DisplayName("every trade has its own identity, and keeps it through every change")
    void atableKeepsItsIdentity() {
        TradeTable table = TradeTable.between(ANA, BEN);
        UUID identity = table.id();

        assertThat(TradeTable.between(ANA, BEN).id()).isNotEqualTo(identity);
        assertThat(table.putUp(ANA, BOLT, 2).id()).isEqualTo(identity);
        assertThat(table.putUp(ANA, BOLT, 2).clearOffer(ANA).id()).isEqualTo(identity);
        assertThat(table.agree(ANA, identity, table.revision()).id()).isEqualTo(identity);
        assertThat(table.agree(ANA, identity, table.revision()).thinkAgain(ANA).id())
                .isEqualTo(identity);
        assertThat(table.close().id()).isEqualTo(identity);
    }

    @Test
    @DisplayName("an agreement naming no table at all is refused")
    void anagreementMustNameATable() {
        TradeTable table = TradeTable.between(ANA, BEN).putUp(ANA, BOLT, 1);

        assertThat(table.agree(ANA, null, table.revision()).hasAgreed(ANA)).isFalse();
        assertThat(table.isStillShowing(null, table.revision())).isFalse();
        assertThat(table.isStillShowing(table.id(), table.revision())).isTrue();
        assertThat(table.isStillShowing(table.id(), table.revision() + 1)).isFalse();
    }

    @Test
    @DisplayName("who is across from whom")
    void thePeopleAtIt() {
        TradeTable table = TradeTable.between(ANA, BEN);

        assertThat(table.across(ANA)).contains(BEN);
        assertThat(table.across(BEN)).contains(ANA);
        assertThat(table.isEmpty()).isTrue();
        assertThat(table.size()).isZero();
    }

    @Property
    @net.jqwik.api.Label("a struck table is one both sides agreed to, unchanged")
    void struckMeansAgreedToThis(@ForAll("moves") java.util.List<Move> moves) {
        TradeTable table = TradeTable.between(ANA, BEN);
        TradeTable whenLastAgreed = null;
        for (Move move : moves) {
            TradeTable before = table;
            table = switch (move.what()) {
                case PUT -> table.putUp(move.who(), move.card(), move.howMany());
                case CLEAR -> table.clearOffer(move.who());
                case AGREE -> table.agree(move.who(), table.id(), table.revision());
                case UNDO -> table.thinkAgain(move.who());
            };
            if (table.isStruck() && !before.isStruck()) {
                whenLastAgreed = table;
            }
            if (table.isStruck()) {
                assertThat(table.fromLeft()).isEqualTo(whenLastAgreed.fromLeft());
                assertThat(table.fromRight()).isEqualTo(whenLastAgreed.fromRight());
            }
            assertThat(table.isStruck())
                    .as("struck is exactly both agreed")
                    .isEqualTo(table.leftAgreed() && table.rightAgreed());
        }
    }

    /** One thing somebody does at a trade table. */
    record Move(UUID who, What what, CardIdentity card, int howMany) {
        enum What { PUT, CLEAR, AGREE, UNDO }
    }

    /**
     * A run of things people do at a trade table.
     * <p>The provider builds the whole list rather than one move: {@code @ForAll("moves")} on
     * a list parameter is resolved against the parameter's own type, so a provider handing
     * back a single move fails the property with an argument mismatch rather than a finding.
     */
    @Provide
    Arbitrary<java.util.List<Move>> moves() {
        return oneMove().list().ofMinSize(1).ofMaxSize(24);
    }

    private Arbitrary<Move> oneMove() {
        return Arbitraries.of(ANA, BEN).flatMap(who ->
                Arbitraries.of(Move.What.values()).flatMap(what ->
                        Arbitraries.of(BOLT, RING).flatMap(card ->
                                Arbitraries.integers().between(0, 3)
                                        .map(howMany -> new Move(who, what, card, howMany)))));
    }
}
