package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.TableBroadcast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Each player at a table is sent the board their own seat is entitled to.
 * <p>The visibility rules decide what a seat may see, and the core proves them. This proves the
 * server asks them about the right seat for the right player: two players with seven cards each,
 * and a third standing by, and each of the three views checked for whose hand it can read.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardAddressGameTest {

    private BoardAddressGameTest() {
    }

    @GameTest(template = "empty")
    public static void everyPlayerIsSentTheirOwnSeatsBoard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = helper.makeMockServerPlayerInLevel();
        ServerPlayer bob = helper.makeMockServerPlayerInLevel();
        ServerPlayer watching = helper.makeMockServerPlayerInLevel();

        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        for (TablePart part : TablePart.values()) {
            level.setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState().setValue(TableBlock.PART, part), 3);
        }
        List<SeatAnchor> seats = TableClusters.at(level, origin).seats();
        TableSeats.take(level, origin, seats.get(0).cell(), seats.get(0).side(), alice.getUUID());
        TableSeats.take(level, origin, seats.get(1).cell(), seats.get(1).side(), bob.getUUID());
        TableSessions.start(level, origin, MatchRules.single(FormatPresets.defaultPreset()));
        GameSession session = TableSessions.sessionAt(level, origin).orElse(null);
        SeatId aliceSeat = TableSessions.seatIdOf(level, origin, alice.getUUID()).orElse(null);
        SeatId bobSeat = TableSessions.seatIdOf(level, origin, bob.getUUID()).orElse(null);
        if (session == null || aliceSeat == null || bobSeat == null || aliceSeat.equals(bobSeat)) {
            helper.fail("fixture: two players did not end up in two seats of one game");
            return;
        }
        for (ServerPlayer player : List.of(alice, bob)) {
            SeatId seat = player == alice ? aliceSeat : bobSeat;
            session.submit(new GameEvent.SeatTaken(seat,
                    new PlayerRef(player.getUUID(), player.getGameProfile().getName())));
            List<CardIdentity> library = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                library.add(CardIdentity.ofPrinting(new UUID(seat.index() + 1L, index), false));
            }
            session.submit(new GameEvent.DeckLoaded(seat, library, List.of()));
            session.submit(new GameEvent.CardsDrawn(seat, seat, 7));
        }
        watching.teleportTo(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 4.5);

        Map<UUID, GameView> sent = new HashMap<>();
        java.util.function.BiConsumer<UUID, GameView> before = TableBroadcast.builtForTesting;
        TableBroadcast.builtForTesting = (player, view) -> {
            if (player.equals(alice.getUUID()) || player.equals(bob.getUUID())
                    || player.equals(watching.getUUID())) {
                sent.put(player, view);
            }
        };
        try {
            TableBroadcast.sendToTable(level, origin);
        } finally {
            TableBroadcast.builtForTesting = before;
        }

        String wrong = readsOnly(sent.get(alice.getUUID()), "Alice", aliceSeat, bobSeat);
        if (wrong == null) {
            wrong = readsOnly(sent.get(bob.getUUID()), "Bob", bobSeat, aliceSeat);
        }
        if (wrong == null) {
            GameView theirs = sent.get(watching.getUUID());
            if (theirs == null) {
                wrong = "somebody standing by the table was sent no board";
            } else if (handsReadIn(theirs, aliceSeat) > 0 || handsReadIn(theirs, bobSeat) > 0) {
                wrong = "somebody standing by the table was sent a hand";
            }
        }
        if (wrong != null) {
            helper.fail(wrong);
            return;
        }
        helper.succeed();
    }

    /** Null if this view reads all of its own seat's hand and none of the other's. */
    private static String readsOnly(GameView view, String who, SeatId own, SeatId other) {
        if (view == null) {
            return who + " was sent no board";
        }
        if (handsReadIn(view, own) != 7) {
            return who + " was sent a board that shows " + handsReadIn(view, own) + " of their own seven cards";
        }
        if (handsReadIn(view, other) != 0) {
            return who + " was sent a board that shows the other player's hand";
        }
        return null;
    }

    /** How many cards of this seat's hand the view names. */
    private static int handsReadIn(GameView view, SeatId seat) {
        return (int) view.seat(seat).zone(Zone.HAND).cards().stream()
                .filter(CardView::carriesIdentity)
                .count();
    }
}
