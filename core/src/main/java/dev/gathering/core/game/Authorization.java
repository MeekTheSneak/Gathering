package dev.gathering.core.game;

import dev.gathering.core.game.event.GameEvent;
import java.util.Optional;

/**
 * The only thing the mod ever says no to.
 *
 * <p>There is no rules enforcement here and there never will be. If the group misplays, the
 * group misplays: a player may tap an already-tapped creature, set their life to minus
 * eleven, move an opponent's commander to their own side of the table, or draw six cards on
 * turn one. Every one of those is attributed by name in the event log, and that attribution
 * is the mechanism - not a permission check.
 *
 * <p>What is checked is information. The single security property of the mod is that no
 * hidden card identity ever reaches a client the visibility rules do not entitle, and an
 * action that would <em>let the actor see</em> a card they are not entitled to is the one
 * way a player could subvert that from inside. So the rule is exactly this narrow:
 *
 * <blockquote>An action is owner-locked if performing it would reveal hidden information to
 * the actor.</blockquote>
 *
 * <p>That is a smaller set than "anything touching a hidden zone", and deliberately so.
 * Making an opponent draw is legal Magic and reveals nothing to the person doing it - the
 * card lands in a hand only its owner can read. Shuffling an opponent's library is legal
 * Magic and reveals nothing either. Both are allowed. Searching that library is not, because
 * searching means looking.
 */
public final class Authorization {

    private Authorization() {
    }

    /**
     * @return empty when the action may proceed, or the reason it may not
     */
    public static Optional<String> denialFor(GameState state, GameEvent event) {
        if (state.ended()) {
            return Optional.of("This session has ended.");
        }
        if (!state.hasSeat(event.actor())) {
            // Spectators receive the public payload set and submit nothing. A spectating
            // client is incapable of acting even if modified, because there is no seat to act as.
            return Optional.of("Only seated players can act at this table.");
        }

        return switch (event) {
            // Looking is the whole of the restriction, and these four are looking.
            case GameEvent.LibrarySearched searched -> ownerOnly(event.actor(), searched.seat(), "search a library");
            case GameEvent.LibraryLooked looked -> ownerOnly(event.actor(), looked.seat(), "look at a library");
            case GameEvent.LibraryReordered reordered ->
                    ownerOnly(event.actor(), reordered.seat(), "reorder a library");
            case GameEvent.Surveiled surveiled -> ownerOnly(event.actor(), surveiled.seat(), "surveil");

            // Naming a specific card inside a hidden zone means having seen it.
            case GameEvent.CardMoved moved -> movingOutOfHiddenZone(state, moved);

            // Emptying a hidden zone into the open shows the actor everything in it, which is
            // the same looking the four above are about - so only its owner may ask for it.
            case GameEvent.ZoneMoved moved -> !moved.fromRef().isHidden()
                    ? Optional.<String>empty()
                    : ownerOnly(event.actor(), moved.seat(), "empty their "
                            + moved.from().name().toLowerCase(java.util.Locale.ROOT));

            // Your own seat, and nobody else's, for the things that are simply about you.
            case GameEvent.SeatTaken ignored -> Optional.empty();
            case GameEvent.SeatReleased ignored -> Optional.empty();
            case GameEvent.Conceded ignored -> Optional.empty();
            case GameEvent.DeckLoaded ignored -> Optional.empty();

            // Everything else is open to any seated player, on purpose.
            default -> Optional.empty();
        };
    }

    private static Optional<String> ownerOnly(SeatId actor, SeatId owner, String what) {
        return actor.equals(owner)
                ? Optional.empty()
                : Optional.of("Only the owner of that library can " + what + ".");
    }

    private static Optional<String> movingOutOfHiddenZone(GameState state, GameEvent.CardMoved moved) {
        Optional<ZoneRef> from = state.locationOf(moved.card());
        if (from.isEmpty()) {
            return Optional.of("That card is not in this session.");
        }
        ZoneRef source = from.get();
        if (!source.isHidden() || source.seat().equals(moved.actor())) {
            return Optional.empty();
        }
        // Moving a card *into* somebody's hand or library is fine - the actor already knew
        // what it was. It is taking one out that requires having read it.
        return Optional.of("Only the owner can move cards out of their " + source.zone().name().toLowerCase(
                java.util.Locale.ROOT) + ".");
    }
}
