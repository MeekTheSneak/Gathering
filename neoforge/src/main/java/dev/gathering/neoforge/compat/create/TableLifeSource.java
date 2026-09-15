package dev.gathering.neoforge.compat.create;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.ValueListDisplaySource;
import dev.gathering.server.TableBoard;
import java.util.stream.Stream;
import net.createmod.catnip.data.IntAttached;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The life totals of the game on this table, one player a line, with poison beside the name when
 * there is any. A player at zero life or ten poison is shown in red, as the table shows them.
 */
public class TableLifeSource extends ValueListDisplaySource {

    @Override
    protected Stream<IntAttached<MutableComponent>> provideEntries(DisplayLinkContext context, int maxRows) {
        var players = Boards.players(context);
        if (players.isEmpty()) {
            return Stream.of(IntAttached.with(0, Component.translatable("display.gathering.no_game")));
        }
        return players.stream().limit(Math.max(1, maxRows)).map(TableLifeSource::line);
    }

    private static IntAttached<MutableComponent> line(TableBoard.Player player) {
        MutableComponent name = player.poison() > 0
                ? Component.translatable("display.gathering.life_with_poison", player.name(), player.poison())
                : Component.literal(player.name() + " ");
        if (player.isAtALoss()) {
            name = name.withStyle(ChatFormatting.RED);
        }
        return IntAttached.with(player.life(), name);
    }

    @Override
    protected boolean valueFirst() {
        return false;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }
}
