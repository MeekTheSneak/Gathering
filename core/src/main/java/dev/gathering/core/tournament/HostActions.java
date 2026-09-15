package dev.gathering.core.tournament;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Which of a host's controls apply to an event as it stands, and why a control does not.
 * <p>One answer for two askers. The server refuses a host's action with it, and sends it with the
 * event so the host's screen can gray out what would be refused, with the reason on the button -
 * rather than offering "Begin" in the middle of round three and explaining afterwards. The screen
 * never decides anything: a forged or stale action is refused here on the server regardless.
 * <p>Pure.
 */
public final class HostActions {

    /** The controls on a host's tab. */
    public enum Action {
        OPEN_CHECK_IN, BEGIN, START_NOW, ADD_TABLES, ADD_PRIZE, MARK_REGISTRATION, CANCEL
    }

    private HostActions() {
    }

    /**
     * Why this action does not apply to this event now, as a lang key, or empty if it does.
     *
     * @param packsStillOut whether a limited event's packs are still being signed up for, opened
     *                      or drafted - which only the server's world can say
     */
    public static Optional<String> refusal(Action action, Tournament tournament, boolean packsStillOut) {
        Tournament.Phase phase = tournament.phase();
        boolean signingUp = phase == Tournament.Phase.SIGNUP || phase == Tournament.Phase.CHECK_IN;
        return switch (action) {
            case OPEN_CHECK_IN -> phase == Tournament.Phase.SIGNUP
                    ? Optional.empty() : Optional.of("message.gathering.event.not_signing_up");
            case BEGIN -> !signingUp
                    ? Optional.of("message.gathering.event.not_signing_up")
                    : playersIfBegunNow(tournament) < tournament.fewestPlayers()
                            ? Optional.of("message.gathering.event.too_few")
                            : Optional.empty();
            case START_NOW -> phase != Tournament.Phase.PREPARING
                    ? Optional.of("message.gathering.event.not_preparing")
                    : packsStillOut ? Optional.of("message.gathering.event.pod_still_running") : Optional.empty();
            case MARK_REGISTRATION -> signingUp ? Optional.empty() : Optional.of("message.gathering.event.not_signing_up");
            case ADD_TABLES, ADD_PRIZE, CANCEL -> tournament.isOver()
                    ? Optional.of("message.gathering.event.already_over") : Optional.empty();
        };
    }

    /** Every action's refusal, empty for those that apply: what is sent with the event to its host. */
    public static Map<Action, Optional<String>> all(Tournament tournament, boolean packsStillOut) {
        Map<Action, Optional<String>> answers = new EnumMap<>(Action.class);
        for (Action action : Action.values()) {
            answers.put(action, refusal(action, tournament, packsStillOut));
        }
        return answers;
    }

    /** How many would play if registration closed now: at a large event, only those checked in. */
    private static int playersIfBegunNow(Tournament tournament) {
        return tournament.phase() == Tournament.Phase.CHECK_IN
                ? (int) tournament.entrants().stream().filter(entrant -> tournament.checkedIn().contains(entrant.id())).count()
                : tournament.entrants().size();
    }
}
