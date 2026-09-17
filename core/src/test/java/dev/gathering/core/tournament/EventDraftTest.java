package dev.gathering.core.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.draft.PodSettings;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a host has chosen while making a tournament survives everything the create screen does to
 * itself: a subscreen, a rebuild, and the screen being closed and opened again.
 */
class EventDraftTest {

    private static EventDraft drafting() {
        return EventDraft.blank("modern").withKind(EventSettings.Kind.DRAFT);
    }

    @Test
    @DisplayName("pack settings chosen in the subscreen come back when it is opened again")
    void packSettingsAreKept() {
        PodSettings chosen = new PodSettings(PodSettings.Kind.DRAFT, PodSettings.Source.SPONSORED,
                PodSettings.SetRule.oneSet("dom"), 4, 2, PodSettings.CardsGo.PLAYERS_KEEP, 90);
        EventDraft draft = drafting().withPod(chosen);
        assertThat(draft.pod()).isEqualTo(chosen);
        assertThat(draft.settings().pod()).isEqualTo(chosen);
    }

    /**
     * The bug the owner reported: every setting went back to its default. Pressing the kind that is
     * already chosen is what the create screen does whenever the row is pressed again, and it used to
     * throw the host's pack settings away.
     */
    @Test
    @DisplayName("choosing the kind already chosen changes nothing")
    void theKindAlreadyChosenChangesNothing() {
        PodSettings chosen = new PodSettings(PodSettings.Kind.DRAFT, PodSettings.Source.SPONSORED,
                PodSettings.SetRule.oneSet("dom"), 4, 2, PodSettings.CardsGo.PLAYERS_KEEP, 90);
        EventDraft draft = drafting().withPod(chosen).withBuildMinutes(45);
        assertThat(draft.withKind(EventSettings.Kind.DRAFT)).isSameAs(draft);
        assertThat(draft.withKind(EventSettings.Kind.DRAFT).pod()).isEqualTo(chosen);
        assertThat(draft.withKind(EventSettings.Kind.DRAFT).buildMinutes()).isEqualTo(45);
    }

    @Test
    @DisplayName("switching draft to sealed keeps where the packs come from and which sets they are")
    void switchingKindKeepsWhatStillApplies() {
        PodSettings chosen = new PodSettings(PodSettings.Kind.DRAFT, PodSettings.Source.SPONSORED,
                PodSettings.SetRule.oneSet("dom"), 4, 2, PodSettings.CardsGo.PLAYERS_KEEP, 90);
        EventDraft sealed = drafting().withPod(chosen).withKind(EventSettings.Kind.SEALED);
        assertThat(sealed.pod().kind()).isEqualTo(PodSettings.Kind.SEALED);
        assertThat(sealed.pod().source()).isEqualTo(PodSettings.Source.SPONSORED);
        assertThat(sealed.pod().sets()).isEqualTo(PodSettings.SetRule.oneSet("dom"));
        assertThat(sealed.pod().pickSeconds()).isZero();
    }

    @Test
    @DisplayName("the build clock follows the kind until the host moves it, and then stays put")
    void theBuildClockFollowsTheKindOnlyWhileUntouched() {
        EventDraft followed = EventDraft.blank("modern").withKind(EventSettings.Kind.DRAFT);
        assertThat(followed.buildMinutes()).isEqualTo(EventSettings.usualBuildMinutes(EventSettings.Kind.DRAFT));
        EventDraft moved = followed.withBuildMinutes(45).withKind(EventSettings.Kind.SEALED);
        assertThat(moved.buildMinutes()).isEqualTo(45);
    }

    @Test
    @DisplayName("a limited event keeps the constructed format chosen before it, and hands it back")
    void theFormatSurvivesALimitedKind() {
        EventDraft draft = EventDraft.blank("modern").withFormat("legacy").withKind(EventSettings.Kind.SEALED);
        assertThat(draft.settings().formatId()).isEmpty();
        assertThat(draft.withKind(EventSettings.Kind.CONSTRUCTED).settings().formatId()).isEqualTo("legacy");
    }

    @Test
    @DisplayName("every choice reaches the settings the server is sent")
    void everyChoiceReachesTheSettings() {
        EventDraft draft = EventDraft.blank("modern").withBestOf(1).withRoundMinutes(30).withRounds(5).withTopCut(8)
                .withDecks(EventSettings.DeckRegistration.OFF).withLargeEvent(true);
        EventSettings settings = draft.settings();
        assertThat(settings.bestOf()).isEqualTo(1);
        assertThat(settings.roundMinutes()).isEqualTo(30);
        assertThat(settings.rounds()).isEqualTo(5);
        assertThat(settings.topCut()).isEqualTo(8);
        assertThat(settings.decks()).isEqualTo(EventSettings.DeckRegistration.OFF);
        assertThat(settings.largeEvent()).isTrue();
        assertThat(settings.problem()).isEmpty();
    }

    @Test
    @DisplayName("prizes put up before the event exists are kept in the order they were put up")
    void prizesAreKeptInOrder() {
        EventDraft draft = EventDraft.blank("modern")
                .withPrize(new PrizeOffer(1, 0))
                .withPrize(new PrizeOffer(2, 3));
        assertThat(draft.prizes()).containsExactly(new PrizeOffer(1, 0), new PrizeOffer(2, 3));
        assertThat(draft.withoutPrize(0).prizes()).containsExactly(new PrizeOffer(2, 3));
    }

    @Test
    @DisplayName("one slot is one prize, however many times it is put up")
    void oneSlotIsOnePrize() {
        EventDraft draft = EventDraft.blank("modern").withPrize(new PrizeOffer(1, 0)).withPrize(new PrizeOffer(4, 0));
        assertThat(draft.prizes()).containsExactly(new PrizeOffer(1, 0));
    }

    @Test
    @DisplayName("a prize for a place or a slot that does not exist is not put up")
    void anImpossiblePrizeIsRefused() {
        EventDraft draft = EventDraft.blank("modern");
        assertThat(draft.withPrize(new PrizeOffer(0, 0)).prizes()).isEmpty();
        assertThat(draft.withPrize(new PrizeOffer(PrizeOffer.LOWEST_PLACE + 1, 0)).prizes()).isEmpty();
        assertThat(draft.withPrize(new PrizeOffer(1, PrizeOffer.HOTBAR_SLOTS)).prizes()).isEmpty();
        assertThat(draft.withPrize(new PrizeOffer(1, -1)).prizes()).isEmpty();
    }

    @Test
    @DisplayName("what a client sent is read defensively: nulls, nonsense and repeats are dropped")
    void offersAreReadDefensively() {
        assertThat(PrizeOffer.accepted(null)).isEmpty();
        List<PrizeOffer> offered = java.util.Arrays.asList(new PrizeOffer(1, 0), null, new PrizeOffer(2, 0),
                new PrizeOffer(99, 1), new PrizeOffer(3, 8));
        assertThat(PrizeOffer.accepted(offered)).containsExactly(new PrizeOffer(1, 0), new PrizeOffer(3, 8));
    }
}
