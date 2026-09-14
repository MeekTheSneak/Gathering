package dev.gathering.server.events;

import dev.gathering.block.TableBlock;
import dev.gathering.core.tournament.Pairing;
import dev.gathering.core.tournament.Round;
import dev.gathering.core.tournament.Tournament;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The number floating over each of an event's tables, and who is playing there.
 * <p>Kept on the table and sent with it, like its felt, so anybody walking through a venue sees
 * which table is which without opening anything. Drawn as text, so it needs no artwork.
 */
public final class EventLabels {

    private EventLabels() {
    }

    static void update(MinecraftServer server, EventState state) {
        ServerLevel level = Events.levelOf(server, state).orElse(null);
        if (level == null) {
            return;
        }
        Tournament tournament = state.tournament;
        boolean playing = tournament.phase() == Tournament.Phase.SWISS || tournament.phase() == Tournament.Phase.CUT;
        Round round = tournament.currentRound().orElse(null);
        long endsAt = playing && round != null && !round.timeCalled()
                ? level.getGameTime() + Math.max(0, tournament.settings().roundMinutes() * Events.MINUTE - state.roundTicks)
                : 0;
        for (int index = 0; index < state.tables.size(); index++) {
            int number = index + 1;
            BlockPos table = state.tables.get(index);
            String line = "";
            if (playing && round != null) {
                Pairing pairing = round.atTable(number).orElse(null);
                if (pairing != null) {
                    line = Events.nameOf(state, pairing.a()) + " - " + Events.nameOf(state, pairing.b());
                }
            }
            String finalLine = line;
            TableBlock.entityAt(level, table).ifPresent(entity ->
                    entity.setEventLabel(tournament.isOver() ? 0 : number, finalLine, tournament.isOver() ? 0 : endsAt));
        }
    }
}
