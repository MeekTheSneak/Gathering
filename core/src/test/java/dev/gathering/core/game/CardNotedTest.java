package dev.gathering.core.game;

import static dev.gathering.core.game.GameFixtures.ALICE;
import static dev.gathering.core.game.GameFixtures.BOB;
import static dev.gathering.core.game.GameFixtures.twoPlayerTable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.event.LogArg;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.game.visibility.VisibilityRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pen, as a rule.
 *
 * <p>A note is player-written text on a card that the whole table reads, which makes it the
 * one thing on a card that is not the mod's own information - so the tests here are as much
 * about what it must not become as about what it does.
 */
class CardNotedTest {

    @Test
    @DisplayName("what somebody writes stays on the card")
    void writingStays() {
        GameSession session = twoPlayerTable(6);
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 1));
        CardInstanceId card = session.state().contents(ALICE, Zone.HAND).get(0);

        session.submit(new GameEvent.CardNoted(ALICE, card, "flying until end of turn"));

        assertThat(session.state().requireCard(card).writtenOn())
                .contains("flying until end of turn");
    }

    @Test
    @DisplayName("writing nothing rubs it out")
    void nothingRubsItOut() {
        GameSession session = twoPlayerTable(6);
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 1));
        CardInstanceId card = session.state().contents(ALICE, Zone.HAND).get(0);
        session.submit(new GameEvent.CardNoted(ALICE, card, "morph"));

        session.submit(new GameEvent.CardNoted(ALICE, card, "   "));

        assertThat(session.state().requireCard(card).writtenOn()).isEmpty();
    }

    @Test
    @DisplayName("anybody may write on a public card, and the log says who")
    void anybodyMayWrite() {
        GameSession session = twoPlayerTable(6);
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 1));
        CardInstanceId card = session.state().contents(ALICE, Zone.HAND).get(0);
        session.submit(new GameEvent.CardMoved(
                ALICE, card, ZoneRef.of(ALICE, Zone.BATTLEFIELD), Placement.at(1000, 1000)));

        GameSession.Result result = session.submit(new GameEvent.CardNoted(BOB, card, "mine now"));

        assertThat(result).isInstanceOf(GameSession.Result.Accepted.class);
        var line = session.log().get(session.log().size() - 1);
        assertThat(line.key()).isEqualTo("log.gathering.card_written_on");
        assertThat(line.args()).contains(new LogArg.Seat(BOB));
    }

    @Test
    @DisplayName("the log never repeats what was written")
    void theLogDoesNotRepeatIt() {
        GameSession session = twoPlayerTable(6);
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 1));
        CardInstanceId card = session.state().contents(ALICE, Zone.HAND).get(0);
        session.submit(new GameEvent.CardNoted(ALICE, card, "a secret plan"));

        for (var entry : session.log()) {
            for (Object arg : entry.args()) {
                assertThat(String.valueOf(arg)).doesNotContain("a secret plan");
            }
        }
    }

    @Test
    @DisplayName("a note on a face-down card reaches everybody, and its name still does not")
    void aNoteOnAFaceDownCardIsPublic() {
        GameSession session = twoPlayerTable(6);
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 1));
        CardInstanceId card = session.state().contents(ALICE, Zone.HAND).get(0);
        session.submit(new GameEvent.CardMoved(
                ALICE, card, ZoneRef.of(ALICE, Zone.BATTLEFIELD), Placement.at(1000, 1000)));
        session.submit(new GameEvent.CardFacingSet(ALICE, card, Facing.FACE_DOWN));
        session.submit(new GameEvent.CardNoted(ALICE, card, "2/2 morph"));

        GameView theirs = VisibilityRules.viewFor(session.state(), new Viewer.Seated(BOB));
        List<CardView> onTheTable = theirs.seat(ALICE).zone(Zone.BATTLEFIELD).cards();
        assertThat(onTheTable).hasSize(1);
        CardView seen = onTheTable.get(0);
        assertThat(seen).isInstanceOf(CardView.Anonymous.class);
        assertThat(seen.writtenOn()).contains("2/2 morph");
    }

    @Test
    @DisplayName("undoing a note puts back what was written before")
    void undoPutsTheOldNoteBack() {
        GameSession session = twoPlayerTable(6);
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 1));
        CardInstanceId card = session.state().contents(ALICE, Zone.HAND).get(0);
        session.submit(new GameEvent.CardNoted(ALICE, card, "first"));
        session.submit(new GameEvent.CardNoted(ALICE, card, "second"));

        session.undo(ALICE, 1, List.of());

        assertThat(session.state().requireCard(card).writtenOn()).contains("first");
    }

    @Test
    @DisplayName("a note goes with the card wherever it goes")
    void theNoteTravels() {
        GameSession session = twoPlayerTable(6);
        session.submit(new GameEvent.CardsDrawn(ALICE, ALICE, 1));
        CardInstanceId card = session.state().contents(ALICE, Zone.HAND).get(0);
        session.submit(new GameEvent.CardNoted(ALICE, card, "keep an eye on this"));
        session.submit(new GameEvent.CardMoved(
                ALICE, card, ZoneRef.of(ALICE, Zone.GRAVEYARD), Placement.TOP));

        assertThat(session.state().requireCard(card).writtenOn()).contains("keep an eye on this");
    }
}
