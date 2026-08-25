package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.match.MatchState;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.TableCluster;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.TableMatch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A set of games, not just one.
 *
 * <p>Best-of-three is how the sixty-card formats are actually played, and the arithmetic of it
 * is the kind that looks obviously right and stops at 1-1: a match at one game each is on game
 * three, which has not been played. So these run whole sets to their end and check the table
 * is in the state a set that far along should be in - board gone, decks kept, score kept - and
 * that a decided match hands the decks back rather than holding them forever.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MatchGameTest {

    private static final UUID SOL_RING = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");

    @GameTest(template = "empty")
    public static void aBestOfThreeAtOneEachIsNotOver(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper);
        startMatch(helper, origin, 3);

        winGame(helper, origin, new SeatId(0));
        startNextGame(helper, origin);
        winGame(helper, origin, new SeatId(1));

        MatchState match = TableSessions.matchAt(helper.getLevel(), origin).orElse(null);
        if (match == null) {
            helper.fail("The table forgot the match at one game each");
            return;
        }
        if (match.isDecided()) {
            helper.fail("A best of three was decided at one game each");
        }
        if (!match.hasGameToPlay()) {
            helper.fail("A best of three at one each has no game left to play");
        }
        if (match.gameNumber() != 3) {
            helper.fail("At one game each the match should be on game 3, not " + match.gameNumber());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void betweenGamesTheBoardGoesAndTheDecksStay(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper);
        startMatch(helper, origin, 3);
        tableAt(helper, origin).holdDeck(new SeatId(0), deck(), null);

        winGame(helper, origin, new SeatId(0));

        if (TableSessions.hasSession(helper.getLevel(), origin)) {
            helper.fail("The finished game is still running");
        }
        if (!TableMatch.isBetweenGames(helper.getLevel(), origin)) {
            helper.fail("The table does not know it is between games");
        }
        if (tableAt(helper, origin).deckOf(new SeatId(0)).isEmpty()) {
            helper.fail("The table gave a deck back in the middle of a match");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void takingTheMatchEndsItAndReturnsTheDecks(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper);
        clearItems(helper, origin);
        startMatch(helper, origin, 3);
        tableAt(helper, origin).holdDeck(new SeatId(0), deck(), null);

        winGame(helper, origin, new SeatId(0));
        startNextGame(helper, origin);
        winGame(helper, origin, new SeatId(0));

        if (TableMatch.isBetweenGames(helper.getLevel(), origin)) {
            helper.fail("A match won two-nil is still between games");
        }
        if (tableAt(helper, origin).match().isPresent()) {
            helper.fail("The table is still holding a finished match");
        }
        if (!tableAt(helper, origin).heldDecks().isEmpty()) {
            helper.fail("The table kept the decks after the match ended");
        }
        if (deckOnTheFloor(helper, origin).isEmpty()) {
            helper.fail("The decks did not come back when the match ended");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSingleGameEndsTheWholeThing(GameTestHelper helper) {
        // Commander, best of one: winning the game is winning the set, and there is no
        // between-games state to be in.
        BlockPos origin = seatedTable(helper);
        startMatch(helper, origin, 1);

        winGame(helper, origin, new SeatId(0));

        if (TableMatch.isBetweenGames(helper.getLevel(), origin)) {
            helper.fail("A single game left the table waiting for another one");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void thenextGameKeepsTheScoreAndPutsTheDecksBackDown(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper);
        startMatch(helper, origin, 3);
        tableAt(helper, origin).holdDeck(new SeatId(0), deck(), null);

        winGame(helper, origin, new SeatId(0));
        startNextGame(helper, origin);

        MatchState match = TableSessions.matchAt(helper.getLevel(), origin).orElse(null);
        if (match == null || match.winsFor(new SeatId(0)) != 1) {
            helper.fail("The next game forgot who won the first one");
            return;
        }
        // Players keep their decks between games of a match; making them hand it over again
        // each time would be ceremony with a chance of getting it wrong.
        int library = TableSessions.sessionAt(helper.getLevel(), origin)
                .map(session -> session.state().count(
                        dev.gathering.core.game.ZoneRef.of(new SeatId(0), dev.gathering.core.game.Zone.LIBRARY)))
                .orElse(0);
        if (library != deck().entries().size()) {
            helper.fail("The held deck did not go back down for game two: library is " + library);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sideboardingOnlyHappensWhereAFormatHasOne(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper);
        // Commander has no sideboard, so a best-of-three of it still never offers one.
        startMatch(helper, origin, 3);

        winGame(helper, origin, new SeatId(0));

        if (!TableMatch.isBetweenGames(helper.getLevel(), origin)) {
            helper.fail("A best of three stopped after one game");
        }
        if (TableMatch.isSideboarding(helper.getLevel(), origin)) {
            helper.fail("Commander was offered a sideboard");
        }
        helper.succeed();
    }

    /**
     * Winning a game of a set names whoever won it.
     *
     * <p>The sentence is the only visible result of a game ending, and it was wrong for
     * months with nothing to notice: the line was written after the board was put away, and a
     * seat's name lives in the session, so every game of a set except the last credited an
     * empty chair. The last one was right, which is exactly why nobody saw it.
     */
    @GameTest(template = "empty")
    public static void aWonGameOfASetNamesWhoWonIt(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper);
        startMatch(helper, origin, 3);

        var session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        String winner = dev.gathering.SeatNames.of(session.state().seatState(new SeatId(0)))
                .getString();
        session.submit(new GameEvent.Conceded(new SeatId(1)));
        net.minecraft.network.chat.Component line =
                TableMatch.settleIfFinished(helper.getLevel(), origin, session.state());

        if (line == null) {
            helper.fail("A conceded game of a set said nothing at all");
            return;
        }
        String said = line.getString();
        if (!said.contains(winner)) {
            helper.fail("Winning game one was announced as \"" + said + "\", which does not"
                    + " name the winner (" + winner + ")");
            return;
        }
        // And the set is genuinely still going, so this is the branch that used to be wrong.
        if (!TableMatch.isBetweenGames(helper.getLevel(), origin)) {
            helper.fail("Winning game one of three did not leave the table between games");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSoloGameKeepsRunningWhileYouPlayIt(GameTestHelper helper) {
        // Goldfishing: one player, one table, no opponent. This broke because one player left
        // standing out of one looked like a last player standing, so the game settled itself
        // on the first action anybody took and the board vanished.
        BlockPos origin = seatedTable(helper, 1);
        startMatch(helper, origin, 1);

        var session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        session.submit(new GameEvent.LifeChanged(new SeatId(0), new SeatId(0), -3));
        TableMatch.settleIfFinished(helper.getLevel(), origin, session.state());

        if (!TableSessions.hasSession(helper.getLevel(), origin)) {
            helper.fail("A solo game ended itself as soon as it was played");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSoloGameEndsWhenYouScoop(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper, 1);
        startMatch(helper, origin, 1);

        var session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        session.submit(new GameEvent.Conceded(new SeatId(0)));
        TableMatch.settleIfFinished(helper.getLevel(), origin, session.state());

        if (TableSessions.hasSession(helper.getLevel(), origin)) {
            helper.fail("Scooping a solo game left it running");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crouchingOnAnEmptyTableSitsYouDown(GameTestHelper helper) {
        // The first thing anybody does alone is put a table down and crouch on it. Making that
        // fail because they had not clicked an edge first is how a mod looks broken to
        // somebody trying it for the first time.
        BlockPos origin = place(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);

        TableBlock.startGameFor(helper.getLevel(), origin, player);

        if (TableSeats.seatOf(helper.getLevel(), origin, player.getUUID()).isEmpty()) {
            helper.fail("Crouching on an empty table did not sit the player down");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sittingDownDoesNotMoveSomebodyAlreadySeated(GameTestHelper helper) {
        BlockPos origin = place(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        List<SeatAnchor> anchors = dev.gathering.block.TableClusters.at(helper.getLevel(), origin).seats();
        SeatAnchor chosen = anchors.get(anchors.size() - 1);
        TableSeats.take(helper.getLevel(), origin, chosen.cell(), chosen.side(), player.getUUID());

        TableBlock.startGameFor(helper.getLevel(), origin, player);

        SeatAnchor now = TableSeats.seatOf(helper.getLevel(), origin, player.getUUID()).orElse(null);
        if (now == null || now.side() != chosen.side()) {
            helper.fail("Starting a game moved a player who already had a seat");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------- fixtures

    private static void startMatch(GameTestHelper helper, BlockPos origin, int bestOf) {
        TableSessions.start(helper.getLevel(), origin,
                new MatchRules(FormatPresets.COMMANDER, bestOf));
    }

    /**
     * Plays a game out to a result the only way the mod recognises one: everybody else scoops.
     *
     * <p>Goes through the same settle step the network handler uses, so what is being tested
     * is the path a real concession takes rather than a shortcut around it.
     */
    private static void winGame(GameTestHelper helper, BlockPos origin, SeatId winner) {
        var session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        for (SeatId seat : session.state().seats()) {
            if (!seat.equals(winner) && session.state().seatState(seat).isOccupied()) {
                session.submit(new GameEvent.Conceded(seat));
            }
        }
        TableMatch.settleIfFinished(helper.getLevel(), origin, session.state());
    }

    private static void startNextGame(GameTestHelper helper, BlockPos origin) {
        MatchState match = TableSessions.matchAt(helper.getLevel(), origin).orElseThrow();
        TableSessions.start(helper.getLevel(), origin, match.rules(), match);
    }

    /**
     * A table with two seats claimed.
     *
     * <p>Claimed by bare UUIDs rather than by real players: the seats have to be occupied for
     * a game to start and for a concession to mean anything, and nothing here needs anybody to
     * actually be online.
     */
    private static BlockPos seatedTable(GameTestHelper helper) {
        return seatedTable(helper, 2);
    }

    private static BlockPos seatedTable(GameTestHelper helper, int players) {
        BlockPos origin = place(helper);
        TableCluster cluster = dev.gathering.block.TableClusters.at(helper.getLevel(), origin);
        List<SeatAnchor> anchors = cluster.seats();
        for (int index = 0; index < Math.min(players, anchors.size()); index++) {
            SeatAnchor anchor = anchors.get(index);
            TableSeats.take(helper.getLevel(), origin, anchor.cell(), anchor.side(), new UUID(7L, index));
        }
        return origin;
    }

    private static DeckComponent deck() {
        return new DeckComponent(
                "Match Test", "", Optional.empty(),
                List.of(card(), card(), card(), card()),
                List.of(),
                List.of(card(), card()));
    }

    private static CardComponent card() {
        return CardComponent.of(CardIdentity.ofPrinting(SOL_RING));
    }

    private static TableBlockEntity tableAt(GameTestHelper helper, BlockPos origin) {
        return TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
    }

    private static Optional<DeckComponent> deckOnTheFloor(GameTestHelper helper, BlockPos origin) {
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(6.0d))
                .stream()
                .map(ItemEntity::getItem)
                .map(dev.gathering.item.DeckItem::deckOf)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** The world these run in is on disk between runs, so leftovers would hide a failure. */
    private static void clearItems(GameTestHelper helper, BlockPos origin) {
        helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8.0d))
                .forEach(ItemEntity::discard);
    }

    private static BlockPos place(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState table = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(
                    part.offsetFrom(origin), table.setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }
}
