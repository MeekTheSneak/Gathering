package dev.gathering.core.game.event;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Facing;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.ZoneRef;
import java.util.List;

/**
 * The events that put a card down face down, in the only order that keeps it a secret.
 * <p>One payload carries one event, and the server sends the whole table a fresh board after
 * each one. So playing a card face down is two boards, not one - and the order decides what
 * is on the first of them. Moving first and turning it down after puts the card face up on
 * the battlefield for one broadcast, and a face-up card on the battlefield is entitled to
 * everybody: its name and its picture reach every opponent and every spectator before the
 * second event turns it down. The screen showed it for a single frame; the packet is on their
 * disk forever.
 * <p>Turning it down first costs nothing and leaks nothing. A card in hand is hidden from
 * everyone but its owner whichever way it faces, so the first board says only that something
 * in a hand changed; the second shows a face-down card arriving on the battlefield, which is
 * exactly what the player asked for.
 * <p>Written here rather than at the menu entry that sends it, because the order <em>is</em>
 * the security property and a property that lives in a click handler is one nothing can
 * check.
 */
public final class PlayingFaceDown {

    private PlayingFaceDown() {
    }

    /** Turn it down, then put it down. Never the other way round. */
    public static List<GameEvent> onto(
            SeatId actor, CardInstanceId card, ZoneRef to, Placement placement) {
        return List.of(
                new GameEvent.CardFacingSet(actor, card, Facing.FACE_DOWN),
                new GameEvent.CardMoved(actor, card, to, placement));
    }
}
