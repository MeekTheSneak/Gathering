package dev.gathering.core.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A host is offered exactly the controls the event would take, in every phase it passes through. */
class HostActionsTest {

    private static Tournament signup(int players) {
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Friday", new UUID(1L, 1L),
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        for (int index = 0; index < players; index++) {
            tournament = tournament.register(Entrant.registering(new UUID(2L, index), "P" + index, 1500));
        }
        return tournament;
    }

    private static boolean offered(HostActions.Action action, Tournament tournament) {
        return HostActions.refusal(action, tournament, false).isEmpty();
    }

    @Test
    @DisplayName("while signing up: check-in, begin, where to register, tables, prizes and calling off")
    void signingUp() {
        Tournament tournament = signup(4);
        assertThat(offered(HostActions.Action.OPEN_CHECK_IN, tournament)).isTrue();
        assertThat(offered(HostActions.Action.BEGIN, tournament)).isTrue();
        assertThat(offered(HostActions.Action.MARK_REGISTRATION, tournament)).isTrue();
        assertThat(offered(HostActions.Action.START_NOW, tournament)).isFalse();
        assertThat(offered(HostActions.Action.CANCEL, tournament)).isTrue();
    }

    @Test
    @DisplayName("begin says there are too few players rather than being offered")
    void tooFewToBegin() {
        assertThat(HostActions.refusal(HostActions.Action.BEGIN, signup(1), false))
                .contains("message.gathering.event.too_few");
    }

    @Test
    @DisplayName("at check-in, begin counts only those checked in")
    void checkInCountsTheCheckedIn() {
        Tournament tournament = signup(4).openCheckIn().checkIn(new UUID(2L, 0));
        assertThat(HostActions.refusal(HostActions.Action.BEGIN, tournament, false))
                .contains("message.gathering.event.too_few");
        assertThat(offered(HostActions.Action.OPEN_CHECK_IN, tournament)).isFalse();
        tournament = tournament.checkIn(new UUID(2L, 1));
        assertThat(offered(HostActions.Action.BEGIN, tournament)).isTrue();
    }

    @Test
    @DisplayName("during play none of registration's controls are offered, and each says why")
    void duringPlay() {
        Tournament tournament = signup(4).beginPreparing();
        for (int index = 0; index < 4; index++) {
            tournament = tournament.markReady(new UUID(2L, index));
        }
        tournament = tournament.startSwiss();
        for (HostActions.Action action : new HostActions.Action[] {
                HostActions.Action.OPEN_CHECK_IN, HostActions.Action.BEGIN, HostActions.Action.MARK_REGISTRATION}) {
            assertThat(HostActions.refusal(action, tournament, false)).as(action.name())
                    .contains("message.gathering.event.not_signing_up");
        }
        assertThat(HostActions.refusal(HostActions.Action.START_NOW, tournament, false))
                .contains("message.gathering.event.not_preparing");
        assertThat(offered(HostActions.Action.ADD_PRIZE, tournament)).isTrue();
        assertThat(offered(HostActions.Action.CANCEL, tournament)).isTrue();
    }

    @Test
    @DisplayName("start now waits for the packs, and is offered only while preparing")
    void startNow() {
        Tournament preparing = signup(4).beginPreparing();
        assertThat(offered(HostActions.Action.START_NOW, preparing)).isTrue();
        assertThat(HostActions.refusal(HostActions.Action.START_NOW, preparing, true))
                .contains("message.gathering.event.pod_still_running");
    }

    @Test
    @DisplayName("once over, nothing is offered")
    void over() {
        Tournament cancelled = signup(4).cancel();
        for (HostActions.Action action : HostActions.Action.values()) {
            assertThat(offered(action, cancelled)).as(action.name()).isFalse();
        }
    }
}
