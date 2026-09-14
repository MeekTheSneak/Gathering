package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.ui.BulkLimit;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.TableActionsPayload;
import dev.gathering.server.TableActions;
import dev.gathering.server.TableBroadcast;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A verb on a selection, sent as one batch: the same game as the same moves sent one at a
 * time, and one board where there were one per card.
 * <p>The saving is one test. Everything else here is about what must not change: every move
 * still passes every gate on its own, a refused one is refused without taking its neighbours
 * with it, the order holds, a move that ends the game is settled before anything after it is
 * looked at, and the server bounds what one payload can make it do regardless of what the
 * client promises.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BatchedActionsGameTest {

    private static BlockPos table(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }

    /** One seated player standing at a game with this many blank cards on their battlefield. */
    private static SeatId seatedWithCards(
            GameTestHelper helper, ServerPlayer player, BlockPos origin, int howMany) {
        player.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        var seat = TableClusters.at(helper.getLevel(), origin).seats().getFirst();
        if (TableSeats.take(helper.getLevel(), origin, seat.cell(), seat.side(), player.getUUID())
                != TableSeats.Claim.TAKEN) {
            throw new AssertionError("could not seat the player");
        }
        if (TableSessions.start(helper.getLevel(), origin,
                new MatchRules(FormatPresets.COMMANDER, 1)) != TableSessions.Outcome.STARTED) {
            throw new AssertionError("could not start the game");
        }
        SeatId me = TableSessions.seatIdOf(helper.getLevel(), origin, player.getUUID()).orElseThrow();
        GameSession session = session(helper, origin);
        for (int card = 0; card < howMany; card++) {
            session.submit(new GameEvent.PaperCardCreated(me, me, PaperStock.BLANK, "card"));
        }
        return me;
    }

    private static GameSession session(GameTestHelper helper, BlockPos origin) {
        return TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
    }

    private static List<CardInstanceId> battlefield(GameTestHelper helper, BlockPos origin, SeatId seat) {
        return session(helper, origin).state().contents(seat, Zone.BATTLEFIELD);
    }

    private static int tappedAmong(GameTestHelper helper, BlockPos origin, List<CardInstanceId> cards) {
        int tapped = 0;
        for (CardInstanceId card : cards) {
            if (session(helper, origin).state().requireCard(card).tapped()) {
                tapped++;
            }
        }
        return tapped;
    }

    private static TableActionsPayload batch(BlockPos origin, List<byte[]> events) {
        return new TableActionsPayload(origin, events);
    }

    private static byte[] encode(GameEvent event) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
                dev.gathering.core.game.persistence.EventCodec.write(out, event);
            }
            return bytes.toByteArray();
        } catch (java.io.IOException cannotDescribe) {
            throw new IllegalStateException(cannotDescribe);
        }
    }

    /** Eight taps in one batch are one board, and all eight cards are tapped, in order. */
    @GameTest(template = "empty")
    public static void abatchofmovesisoneboard(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        SeatId me = seatedWithCards(helper, player, origin, 8);
        List<CardInstanceId> cards = battlefield(helper, origin, me);
        List<byte[]> taps = new ArrayList<>();
        for (CardInstanceId card : cards) {
            taps.add(encode(new GameEvent.CardTapSet(me, card, true)));
        }
        int logBefore = session(helper, origin).log().size();

        // What showing the table its board once costs right now, with whoever happens to be
        // standing near it - other tests' stand-in players share this world, so the number is
        // measured rather than assumed to be one.
        TableBroadcast.forgetTheCount();
        TableBroadcast.sendToTable(helper.getLevel(), origin);
        int once = TableBroadcast.boardsSent();

        TableBroadcast.forgetTheCount();
        TableActions.handleAll(player, batch(origin, taps));

        if (TableBroadcast.boardsSent() != once) {
            helper.fail("eight moves in one batch sent " + TableBroadcast.boardsSent()
                    + " boards; showing the table once sends " + once);
            return;
        }
        if (tappedAmong(helper, origin, cards) != 8) {
            helper.fail("only " + tappedAmong(helper, origin, cards) + " of 8 cards were tapped");
            return;
        }
        if (session(helper, origin).log().size() - logBefore != 8) {
            helper.fail("the log gained " + (session(helper, origin).log().size() - logBefore)
                    + " lines for eight moves; a batch must still say who did each");
            return;
        }
        helper.succeed();
    }

    /**
     * Every gate still applies to every move, and a refused move takes nothing else with it.
     * <p>Mixed in among good moves: an unreadable one, one signed with somebody else's seat, one
     * only the server may write, and one the rules refuse. The good ones land; none of the
     * others do anything.
     */
    @GameTest(template = "empty")
    public static void everymoveinabatchpasseseverygateonitsown(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        SeatId me = seatedWithCards(helper, player, origin, 3);
        List<CardInstanceId> cards = battlefield(helper, origin, me);
        SeatId somebodyElse = new SeatId(me.index() + 1);
        CardInstanceId nothing = CardInstanceId.of(987_654);
        int logBefore = session(helper, origin).log().size();

        List<byte[]> moves = List.of(
                encode(new GameEvent.CardTapSet(me, cards.get(0), true)),
                new byte[] {1, 2, 3, 4},
                encode(new GameEvent.CardTapSet(somebodyElse, cards.get(1), true)),
                encode(new GameEvent.DiceRolled(me, 20, 20)),
                encode(new GameEvent.CardTapSet(me, nothing, true)),
                encode(new GameEvent.CardTapSet(me, cards.get(2), true)));
        TableActions.handleAll(player, batch(origin, moves));

        var state = session(helper, origin).state();
        if (!state.requireCard(cards.get(0)).tapped() || !state.requireCard(cards.get(2)).tapped()) {
            helper.fail("a good move in the batch did not land because of a bad one beside it");
            return;
        }
        if (state.requireCard(cards.get(1)).tapped()) {
            helper.fail("a move signed with somebody else's seat was applied from inside a batch");
            return;
        }
        int added = session(helper, origin).log().size() - logBefore;
        if (added != 2) {
            helper.fail("the log gained " + added + " lines; only the two good moves should be there"
                    + " - a server-authored roll or a refused move got through");
            return;
        }
        helper.succeed();
    }

    /**
     * A move that ends the game is settled before anything after it in the batch is looked at.
     * <p>The one ordering a batch could get wrong: showing the board and settling are two steps,
     * and the moves after a game-ending one must find the game already put away - as a separate
     * packet arriving after it would have - rather than landing on a game that is over.
     */
    @GameTest(template = "empty")
    public static void amovethatendsthegameissettledbeforethenextone(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        SeatId me = seatedWithCards(helper, player, origin, 2);
        List<CardInstanceId> cards = battlefield(helper, origin, me);
        GameSession ending = session(helper, origin);

        TableBroadcast.forgetTheCount();
        TableActions.handleAll(player, batch(origin, List.of(
                encode(new GameEvent.Conceded(me)),
                encode(new GameEvent.CardTapSet(me, cards.get(0), true)))));

        if (TableBroadcast.boardsSent() < 1) {
            helper.fail("the board the game ended on was never sent");
            return;
        }
        if (ending.state().requireCard(cards.get(0)).tapped()) {
            helper.fail("a move after the game-ending one was applied to the game that had ended");
            return;
        }
        if (TableSessions.sessionAt(helper.getLevel(), origin).map(now -> now == ending).orElse(false)) {
            helper.fail("the game that ended was still the table's game after the batch");
            return;
        }
        helper.succeed();
    }

    /** However many moves a payload is built with, the server does at most the bound. */
    @GameTest(template = "empty")
    public static void theserverboundsabatchwhateverthepayloadsays(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        int over = BulkLimit.MOST_AT_ONCE + 7;
        SeatId me = seatedWithCards(helper, player, origin, over);
        List<CardInstanceId> cards = battlefield(helper, origin, me);
        List<byte[]> taps = new ArrayList<>();
        for (CardInstanceId card : cards) {
            taps.add(encode(new GameEvent.CardTapSet(me, card, true)));
        }

        TableActions.handleAll(player, batch(origin, taps));

        int tapped = tappedAmong(helper, origin, cards);
        if (tapped != BulkLimit.MOST_AT_ONCE) {
            helper.fail("a payload of " + over + " moves made the server apply " + tapped
                    + "; the bound is " + BulkLimit.MOST_AT_ONCE);
            return;
        }
        helper.succeed();
    }

    /** A player out of reach has the whole batch refused, as a single move would be. */
    @GameTest(template = "empty")
    public static void abatchfromoutofreachdoesnothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        SeatId me = seatedWithCards(helper, player, origin, 2);
        List<CardInstanceId> cards = battlefield(helper, origin, me);
        player.setPos(origin.getX() + 500, origin.getY(), origin.getZ() + 500);

        TableActions.handleAll(player, batch(origin, List.of(
                encode(new GameEvent.CardTapSet(me, cards.get(0), true)),
                encode(new GameEvent.CardTapSet(me, cards.get(1), true)))));

        if (tappedAmong(helper, origin, cards) != 0) {
            helper.fail("a batch from out of reach tapped cards");
            return;
        }
        helper.succeed();
    }

    /**
     * Several people watching share one public board: built once, sent to each.
     * <p>And the seated player still gets their own, because theirs is a different view. Two
     * spectators and one player is three boards sent and two views built.
     */
    @GameTest(template = "empty")
    public static void spectatorsshareonepublicboard(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        seatedWithCards(helper, player, origin, 2);
        for (int watcher = 0; watcher < 2; watcher++) {
            ServerPlayer spectator = helper.makeMockServerPlayerInLevel();
            spectator.setPos(origin.getX() + 1.5, origin.getY(), origin.getZ() + 1.5);
        }

        TableBroadcast.forgetTheCount();
        TableBroadcast.sendToTable(helper.getLevel(), origin);

        if (TableBroadcast.boardsSent() < 3) {
            helper.fail("expected at least three boards for a player and two spectators; sent "
                    + TableBroadcast.boardsSent());
            return;
        }
        int spectatorsServed = TableBroadcast.boardsSent() - 1;
        if (TableBroadcast.viewsBuilt() != 2) {
            helper.fail("built " + TableBroadcast.viewsBuilt() + " views for one seated player and "
                    + spectatorsServed + " spectators; expected 2 - one seated, one public");
            return;
        }
        helper.succeed();
    }
}
