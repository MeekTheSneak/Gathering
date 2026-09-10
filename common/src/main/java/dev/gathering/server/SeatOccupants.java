package dev.gathering.server;

import dev.gathering.core.game.PlayerRef;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * What to call whoever is in a seat.
 * <p>One place, because there are three answers and only one of them was being given. A seat's
 * occupant is a player who is here, a player who is not here right now, or the practice
 * table's demonstration - which is not a player at all and must never be labelled as one.
 * <p>The old line was {@code player == null ? "Player" : name}, which called an offline
 * player "Player" and would have called the demonstration seat "Player" too. A board that
 * says "Player" where a name goes is a board somebody has to ask a question about.
 * <p>Server thread only.
 */
public final class SeatOccupants {

    private SeatOccupants() {
    }

    /**
     * The name and id to record for a seat's occupant.
     * <p>Used when a game starts, which is the moment the session learns who is where. The
     * name is recorded rather than looked up afterwards because a session outlives a login:
     * the log has to keep saying who did what after they have gone home.
     */
    public static PlayerRef of(Level level, UUID occupant) {
        if (occupant == null) {
            return null;
        }
        if (occupant.equals(PracticeTable.demonstrationSeat())) {
            return PracticeTable.demonstrationRef();
        }
        var player = level == null ? null : level.getPlayerByUUID(occupant);
        return player == null
                ? new PlayerRef(occupant, Component.translatable("seat.gathering.away").getString())
                : new PlayerRef(occupant, player.getGameProfile().getName());
    }
}
