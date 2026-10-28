package dev.gathering.core.game;

import static dev.gathering.core.game.GameFixtures.ALICE;
import static dev.gathering.core.game.GameFixtures.BOB;
import static dev.gathering.core.game.GameFixtures.twoPlayerTable;
import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.card.PaperStock;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Blank stock, as a rule.
 * <p>A blank card is the mod's answer to every table state it has no feature for, so what
 * matters about it is that it behaves like the token it is - it lands on the battlefield, it
 * can be thrown away, it can be rewritten with the ordinary pen - and that the words on it
 * reach everybody, because a marker only one player can read is not a marker.
 */
class PaperCardTest {

    private static CardInstanceId onlyCard(GameSession session, SeatId seat) {
        List<CardInstanceId> battlefield = session.state().contents(seat, Zone.BATTLEFIELD);
        assertThat(battlefield).hasSize(1);
        return battlefield.get(0);
    }

    @Test
    @DisplayName("a blank card arrives on the battlefield with the words already on it")
    void blankStockCarriesItsWriting() {
        GameSession session = twoPlayerTable(6);

        session.submit(new GameEvent.PaperCardCreated(
                ALICE, ALICE, PaperStock.BLANK, "Alice has the monarch"));

        CardInstance paper = session.state().requireCard(onlyCard(session, ALICE));
        assertThat(paper.writtenOn()).contains("Alice has the monarch");
        assertThat(PaperStock.of(paper.identity())).contains(PaperStock.BLANK);
        // A token, which is what makes it disappear at the end rather than being somebody's
        // card that has to go home.
        assertThat(paper.token()).isTrue();
    }

    @Test
    @DisplayName("an emblem is the same object on different stock")
    void anEmblemIsBlankStockToo() {
        GameSession session = twoPlayerTable(6);

        session.submit(new GameEvent.PaperCardCreated(
                ALICE, ALICE, PaperStock.EMBLEM, "You get an emblem with \"Creatures you control get +1/+1\""));

        CardInstance emblem = session.state().requireCard(onlyCard(session, ALICE));
        assertThat(PaperStock.of(emblem.identity())).contains(PaperStock.EMBLEM);
        assertThat(emblem.identity().isCustom()).isTrue();
        // Never a printing, so nothing will ever go looking for one on Scryfall.
        assertThat(emblem.identity().printing()).isEmpty();
    }

    @Test
    @DisplayName("everybody at the table can read it")
    void theWholeTableReadsIt() {
        GameSession session = twoPlayerTable(6);
        session.submit(new GameEvent.PaperCardCreated(
                ALICE, ALICE, PaperStock.EMBLEM, "Opponents cannot untap"));

        List<CardView> asBobSeesThem = VisibilityRules
                .viewFor(session.state(), new Viewer.Seated(BOB))
                .seat(ALICE).zone(Zone.BATTLEFIELD).cards();

        assertThat(asBobSeesThem).hasSize(1);
        assertThat(asBobSeesThem.get(0).writtenOn()).contains("Opponents cannot untap");
    }

    @Test
    @DisplayName("the pen rewrites one rather than needing a second")
    void theOrdinaryPenWorksOnIt() {
        GameSession session = twoPlayerTable(6);
        session.submit(new GameEvent.PaperCardCreated(
                ALICE, ALICE, PaperStock.BLANK, "Alice has the monarch"));
        CardInstanceId paper = onlyCard(session, ALICE);

        session.submit(new GameEvent.CardNoted(BOB, paper, "Bob has the monarch"));

        assertThat(session.state().requireCard(paper).writtenOn()).contains("Bob has the monarch");
    }

    @Test
    @DisplayName("thrown away with the same verb every other token is")
    void itGoesInTheBinLikeAToken() {
        GameSession session = twoPlayerTable(6);
        session.submit(new GameEvent.PaperCardCreated(ALICE, ALICE, PaperStock.BLANK, "a reminder"));
        CardInstanceId paper = onlyCard(session, ALICE);

        session.submit(new GameEvent.TokenRemoved(ALICE, paper));

        assertThat(session.state().contents(ALICE, Zone.BATTLEFIELD)).isEmpty();
    }

    @Test
    @DisplayName("a card with nothing written on it yet is still a card")
    void blankMeansBlank() {
        GameSession session = twoPlayerTable(6);

        GameSession.Result result =
                session.submit(new GameEvent.PaperCardCreated(ALICE, ALICE, PaperStock.BLANK, "   "));

        assertThat(result.isAccepted()).isTrue();
        assertThat(session.state().requireCard(onlyCard(session, ALICE)).writtenOn()).isEmpty();
    }
}
