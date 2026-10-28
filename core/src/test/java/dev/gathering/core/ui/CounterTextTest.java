package dev.gathering.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Facing;
import dev.gathering.core.game.GameFixtures;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.persistence.SessionCipher;
import dev.gathering.core.game.persistence.StoredSession;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What every kind of counter says once it is on a card.
 * <p>Asked for after a real session: "make sure all the different counter types work on the
 * table view. loyalty, -1/-1, power toughness, etc." There are four kinds and they behave
 * differently on purpose - one adds up, one goes in the corner where a card prints its own
 * numbers, one is a name with a count beside it, and one is not a counter at all but a power
 * and toughness somebody typed, which takes the corner off whatever was in it.
 * <p>The rule lives in core rather than in the two things that draw it, so a card carries the
 * same writing on the screen's board and on the board lying on the block - and so the answer
 * for each kind can be stated here rather than looked at in a screenshot.
 */
class CounterTextTest {

    @Test
    @DisplayName("counters that are a power and toughness add up, rather than counting off")
    void powerAndToughnessCountersAddUp() {
        assertThat(lines(one("+1/+1", 3))).containsExactly(line("+3/+3", null));
        assertThat(lines(one("-1/-1", 2))).containsExactly(line("-2/-2", null));
        assertThat(lines(one("+2/+2", 3))).containsExactly(line("+6/+6", null));
        // Mixed signs are a real printing, and each side multiplies on its own.
        assertThat(lines(one("+1/-1", 4))).containsExactly(line("+4/-4", null));
    }

    @Test
    @DisplayName("one of them still reads as itself")
    void oneOfThemReadsAsItself() {
        assertThat(lines(one("+1/+1", 1))).containsExactly(line("+1/+1", null));
        assertThat(lines(one("-1/-1", 1))).containsExactly(line("-1/-1", null));
    }

    /**
     * A pile pressed below none reads as the creature it makes, not as a count of nothing.
     * <p>Nothing in this mod argues with a player, so the minus takes a card past having any
     * - which used to write "+1/+1 x-2" on it, because the arithmetic gave up rather than
     * print the "+-2" it had produced.
     */
    @Test
    @DisplayName("a pile below none still adds up")
    void aPileBelowNoneStillAddsUp() {
        assertThat(lines(one("+1/+1", -2))).containsExactly(line("-2/-2", null));
        assertThat(lines(one("-1/-1", -2))).containsExactly(line("+2/+2", null));
    }

    @Test
    @DisplayName("a card may carry both, because nothing here enforces a rule")
    void bothKindsAtOnce() {
        Map<String, Integer> both = new LinkedHashMap<>();
        both.put("+1/+1", 3);
        both.put("-1/-1", 1);
        assertThat(lines(both)).containsExactly(line("+3/+3", null), line("-1/-1", null));
    }

    @Test
    @DisplayName("a counter with no arithmetic is its name and how many there are")
    void aPlainCounterIsANameAndACount() {
        assertThat(lines(one("charge", 3))).containsExactly(line("Charge", "x3"));
        assertThat(lines(one("stun", 1))).containsExactly(line("Stun", null));
        // Not this mod's word, so not this mod's to rewrite beyond the first letter: a table
        // that agreed on "shield" has agreed on that word.
        assertThat(lines(one("shield", 2))).containsExactly(line("Shield", "x2"));
        // And a name that is already a symbol is left exactly as it is.
        assertThat(lines(one("@!", 2))).containsExactly(line("@!", "x2"));
    }

    @Test
    @DisplayName("loyalty goes in the corner, where a planeswalker prints it")
    void loyaltyGoesInTheCorner() {
        CardView walker = card(one("loyalty", 4), null);
        assertThat(CounterText.cornerNumber(walker)).isEqualTo("4");
        // And not twice: it is in the corner, so it is not also in the stack.
        assertThat(CounterText.linesOn(walker)).isEmpty();
    }

    @Test
    @DisplayName("a written power and toughness takes the corner, and loyalty moves down")
    void writtenStrengthTakesTheCorner() {
        CardView both = card(one("loyalty", 4), "12/12");
        assertThat(CounterText.cornerNumber(both)).isEqualTo("12/12");
        assertThat(CounterText.linesOn(both)).containsExactly(line("Loyalty", "x4"));
    }

    @Test
    @DisplayName("a written power and toughness alone is the corner, with nothing under it")
    void writtenStrengthAlone() {
        CardView written = card(Map.of(), "6/6");
        assertThat(CounterText.cornerNumber(written)).isEqualTo("6/6");
        assertThat(CounterText.linesOn(written)).isEmpty();
    }

    @Test
    @DisplayName("a card with nothing on it has nothing in its corner")
    void nothingOnItSaysNothing() {
        assertThat(CounterText.cornerNumber(card(Map.of(), null))).isNull();
        assertThat(CounterText.linesOn(card(Map.of(), null))).isEmpty();
        assertThat(CounterText.cornerNumber(null)).isNull();
        assertThat(CounterText.linesOn(null)).isEmpty();
    }

    @Test
    @DisplayName("a creature somebody put loyalty on shows it, because that is a table's business")
    void loyaltyOnAnythingShows() {
        assertThat(CounterText.cornerNumber(card(one("loyalty", 1), null))).isEqualTo("1");
    }

    @Test
    @DisplayName("every kind on one card, in the order they were put on")
    void everyKindAtOnce() {
        Map<String, Integer> many = new LinkedHashMap<>();
        many.put("+1/+1", 2);
        many.put("-1/-1", 1);
        many.put("charge", 5);
        many.put("loyalty", 3);
        many.put("stun", 1);
        CardView card = card(many, null);
        assertThat(CounterText.cornerNumber(card)).isEqualTo("3");
        assertThat(CounterText.linesOn(card)).containsExactly(
                line("+2/+2", null), line("-1/-1", null),
                line("Charge", "x5"), line("Stun", null));
    }

    /**
     * A counter whose name is a number nothing can hold is drawn as the name it is.
     * <p>Both boards draw a card's counters through {@link CounterText#linesOn}. This one threw
     * there, on every frame, for every client looking at the table - the block's renderer and
     * the screen's board alike, and neither catches.
     */
    @Test
    @DisplayName("a number too long to be a power is written as typed, with its count")
    void aNumberTooLongToBeAPowerIsWrittenAsTyped() {
        assertThat(lines(one("+9999999999999999999/+1", 1)))
                .containsExactly(line("+9999999999999999999/+1", null));
        assertThat(lines(one("+9999999999999999999/+1", 3)))
                .containsExactly(line("+9999999999999999999/+1", "x3"));
        assertThat(lines(one("+999999999999999999/+1", 10)))
                .containsExactly(line("+999999999999999999/+1", "x10"));
    }

    /**
     * And a game saved with one in it opens again and can be drawn.
     * <p>The counter is part of the session, so a client that crashed on it came back to it after
     * a reconnect, and a server restart brought it back for everybody. This is the whole road a
     * saved table takes to a client: stored, opened, folded, filtered for each viewer, drawn.
     */
    @Test
    @DisplayName("a game saved carrying such a counter opens, and every viewer's board draws it")
    void aSavedGameCarryingOneOpensAndDraws() throws Exception {
        String nineteen = "+9999999999999999999/+1";
        GameSession session = GameFixtures.twoPlayerTable(10);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
        CardInstanceId bear = GameFixtures.firstInHand(session, GameFixtures.ALICE);
        session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, bear,
                ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.BOTTOM));
        assertThat(session.submit(new GameEvent.CounterChanged(GameFixtures.BOB, bear, nineteen, 1))
                .isAccepted())
                .as("any seated player may put it on a public card")
                .isTrue();

        javax.crypto.SecretKey key = SessionCipher.newKey();
        GameSession reopened = StoredSession.of(session, 40, key).restore(key);
        assertThat(reopened.state()).isEqualTo(session.state());

        int drawn = 0;
        for (GameView view : VisibilityRules.allViews(reopened.state()).values()) {
            for (CardView card : view.seat(GameFixtures.ALICE).zone(Zone.BATTLEFIELD).cards()) {
                assertThat(CounterText.linesOn(card)).containsExactly(line(nineteen, null));
                drawn++;
            }
        }
        assertThat(drawn).as("every viewer, the watcher included, has the card to draw").isEqualTo(3);
    }

    private static Map<String, Integer> one(String name, int howMany) {
        return Map.of(name, howMany);
    }

    private static List<CounterText.Line> lines(Map<String, Integer> counters) {
        return CounterText.linesOn(card(counters, null));
    }

    private static CounterText.Line line(String name, String count) {
        return new CounterText.Line(name, count);
    }

    private static CardView card(Map<String, Integer> counters, String strength) {
        return new CardView.Visible(
                CardInstanceId.of(1),
                CardIdentity.ofPrinting(UUID.nameUUIDFromBytes("bear".getBytes()), false),
                new SeatId(0), Facing.FACE_UP, false, counters, null, false, null, null,
                false, strength, false);
    }
}
