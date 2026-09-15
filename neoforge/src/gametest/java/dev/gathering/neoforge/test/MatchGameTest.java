package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
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
import dev.gathering.server.TableMatch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A set of games, not just one.
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

    @GameTest(template = "tables")
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

    @GameTest(template = "tables")
    public static void betweenGamesTheBoardGoesAndTheDecksStay(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper);
        startMatch(helper, origin, 3);
        tableAt(helper, origin).holdDeck(new SeatId(0), deck(), null, null);

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

    @GameTest(template = "tables")
    public static void takingTheMatchEndsItAndReturnsTheDecks(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper);
        clearItems(helper, origin);
        startMatch(helper, origin, 3);
        tableAt(helper, origin).holdDeck(new SeatId(0), deck(), null, null);

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

    @GameTest(template = "tables")
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

    @GameTest(template = "tables")
    public static void thenextGameKeepsTheScoreAndPutsTheDecksBackDown(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper);
        startMatch(helper, origin, 3);
        tableAt(helper, origin).holdDeck(new SeatId(0), deck(), null, null);

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

    @GameTest(template = "tables")
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
     * <p>The sentence is the only visible result of a game ending, and it was wrong for
     * months with nothing to notice: the line was written after the board was put away, and a
     * seat's name lives in the session, so every game of a set except the last credited an
     * empty chair. The last one was right, which is exactly why nobody saw it.
     */
    @GameTest(template = "tables")
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
        // The game that ended, not the one coming: this said "takes game 2 of 3" for game one.
        if (!(line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents told)
                || told.getArgs().length < 2 || !Integer.valueOf(1).equals(told.getArgs()[1])) {
            helper.fail("Winning game one was announced as \"" + said + "\", not as game one");
            return;
        }
        // And the set is genuinely still going, so this is the branch that used to be wrong.
        if (!TableMatch.isBetweenGames(helper.getLevel(), origin)) {
            helper.fail("Winning game one of three did not leave the table between games");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
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

    @GameTest(template = "tables")
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

    @GameTest(template = "tables")
    public static void crouchingOnATableSeatsNobodyAndStartsNothing(GameTestHelper helper) {
        // Seats come from chairs, and only somebody sitting at a table chooses its game. Crouching
        // on one used to sit the player down wherever there was room; the owner asked for chairs
        // to be the one way to sit, and a crouch that seated you was a second way.
        BlockPos origin = place(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);

        TableBlock.startGameFor(helper.getLevel(), origin, player);

        if (TableSeats.seatOf(helper.getLevel(), origin, player.getUUID()).isPresent()) {
            helper.fail("Crouching on a table sat the player down without a chair");
            return;
        }
        if (TableSessions.hasSession(helper.getLevel(), origin)) {
            helper.fail("Crouching on a table nobody sits at started a game");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "tables")
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

    /**
     * A game that ends by concession is kept as a replay.
     * <p>Conceding is the way a game normally ends, and the settle step it goes through never
     * wrote the game down - so the shelf held only games ended by the command, and the
     * "game recorded" line was one nobody at a real table ever read.
     */
    @GameTest(template = "tables")
    public static void aConcededGameIsKeptAsAReplay(GameTestHelper helper) {
        var before = dev.gathering.service.ServerSettings.get().modes().replays();
        boolean switchOn = before != dev.gathering.core.config.GatheringConfig.Replays.PUBLIC;
        try {
            if (switchOn) {
                dev.gathering.server.Settings.set("modes.replays", "public");
            }
            long beforehand = System.currentTimeMillis();
            BlockPos origin = seatedTable(helper);
            startMatch(helper, origin, 1);
            var session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
            session.submit(new GameEvent.Conceded(new SeatId(1)));
            int steps = session.records().size() + 1;

            TableMatch.settleIfFinished(helper.getLevel(), origin, session.state());

            // The newest thing on the shelf is this game. Counted by identity rather than by
            // how many files there are: the shelf has a cap, so once it is full a game that
            // was kept perfectly well leaves the count exactly where it was.
            var newest = dev.gathering.server.Replays.kept().stream().findFirst().orElse(null);
            if (newest == null || newest.when() < beforehand || newest.steps() != steps) {
                helper.fail("A game that ended by concession was not put on the replay shelf"
                        + " (newest=" + (newest == null ? "none"
                        : newest.steps() + " steps at " + newest.when())
                        + ", this game had " + steps + " steps after " + beforehand + ")");
                return;
            }
            helper.succeed();
        } finally {
            if (switchOn) {
                dev.gathering.server.Settings.set("modes.replays", before.toString());
            }
        }
    }

    /**
     * A set's games are not on the replay shelf until the set is over. A replay shows every
     * library in order, and between games the same decks go back down: the first game of a
     * best of three, watched before the second, was the opponent's whole deck.
     */
    @GameTest(template = "tables")
    public static void aSetsGamesWaitForTheSetToEndBeforeTheyCanBeWatched(GameTestHelper helper) {
        var before = dev.gathering.service.ServerSettings.get().modes().replays();
        boolean switchOn = before != dev.gathering.core.config.GatheringConfig.Replays.PUBLIC;
        try {
            if (switchOn) {
                dev.gathering.server.Settings.set("modes.replays", "public");
            }
            // Players of its own, so no other test's games on the shelf are counted as this one's.
            BlockPos origin = place(helper);
            List<SeatAnchor> anchors = dev.gathering.block.TableClusters.at(helper.getLevel(), origin).seats();
            UUID first = UUID.randomUUID();
            TableSeats.take(helper.getLevel(), origin, anchors.get(0).cell(), anchors.get(0).side(), first);
            TableSeats.take(helper.getLevel(), origin, anchors.get(1).cell(), anchors.get(1).side(), UUID.randomUUID());
            startMatch(helper, origin, 3);
            winGame(helper, origin, new SeatId(0));
            if (shelvedFor(first) != 0) {
                helper.fail("The first game of a set was on the replay shelf while the set was still being played");
                return;
            }
            startNextGame(helper, origin);
            winGame(helper, origin, new SeatId(0));
            if (shelvedFor(first) != 2) {
                helper.fail("Once the set was over, " + shelvedFor(first) + " of its 2 games were on the shelf");
                return;
            }
            helper.succeed();
        } finally {
            if (switchOn) {
                dev.gathering.server.Settings.set("modes.replays", before.toString());
            }
        }
    }

    /**
     * Who plays first: chosen at random for the first game, then the loser of the last game of a
     * two-player match (MTR 2.2 gives them the choice, and playing first is the usual one).
     */
    @GameTest(template = "tables")
    public static void theLoserOfAGamePlaysFirstInTheNext(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper);
        startMatch(helper, origin, 3);
        var first = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        boolean chosen = first.records().stream().anyMatch(record -> record instanceof dev.gathering.core.game.SessionRecord.EventRecord event
                && event.event() instanceof GameEvent.StartingPlayerChosen starting && starting.why() == GameEvent.StartingPlayerChosen.Why.RANDOM);
        if (!chosen) {
            helper.fail("the first game of a match did not choose who plays first");
            return;
        }
        winGame(helper, origin, new SeatId(0));
        startNextGame(helper, origin);
        var second = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        if (!second.state().turn().activeSeat().equals(new SeatId(1))) {
            helper.fail("the player who lost game one is not first in game two: " + second.state().turn().activeSeat());
            return;
        }
        helper.succeed();
    }

    /**
     * After a drawn game the player who was chosen for it goes first again (MTR 2.2), rather than
     * the table choosing at random: the game is conceded by both at once, which is a draw.
     */
    @GameTest(template = "tables")
    public static void whoeverWentFirstInADrawnGameGoesFirstAgain(GameTestHelper helper) {
        BlockPos origin = seatedTable(helper);
        startMatch(helper, origin, 3);
        var first = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        SeatId chosen = startingPlayerOf(first).map(GameEvent.StartingPlayerChosen::actor).orElse(null);
        if (chosen == null) {
            helper.fail("the first game did not choose who plays first");
            return;
        }
        for (SeatId seat : first.state().seats()) {
            if (first.state().seatState(seat).isOccupied()) {
                first.submit(new GameEvent.Conceded(seat));
            }
        }
        TableMatch.settleIfFinished(helper.getLevel(), origin, first.state());
        startNextGame(helper, origin);
        var second = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        var again = startingPlayerOf(second).orElse(null);
        if (again == null || again.why() != GameEvent.StartingPlayerChosen.Why.CHOSE_FOR_THE_DRAWN_GAME
                || !again.actor().equals(chosen) || !second.state().turn().activeSeat().equals(chosen)) {
            helper.fail("after a drawn game " + chosen + " should go first again, but the table said " + again);
            return;
        }
        helper.succeed();
    }

    private static Optional<GameEvent.StartingPlayerChosen> startingPlayerOf(dev.gathering.core.game.GameSession session) {
        return session.records().stream()
                .filter(record -> record instanceof dev.gathering.core.game.SessionRecord.EventRecord)
                .map(record -> ((dev.gathering.core.game.SessionRecord.EventRecord) record).event())
                .filter(GameEvent.StartingPlayerChosen.class::isInstance)
                .map(GameEvent.StartingPlayerChosen.class::cast)
                .findFirst();
    }

    private static long shelvedFor(UUID player) {
        return dev.gathering.server.Replays.kept().stream().filter(kept -> kept.wasPlayedBy(player)).count();
    }

    private static void startMatch(GameTestHelper helper, BlockPos origin, int bestOf) {
        TableSessions.start(helper.getLevel(), origin,
                new MatchRules(FormatPresets.COMMANDER, bestOf));
    }

    /**
     * Plays a game out to a result the only way the mod recognizes one: everybody else scoops.
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
        return TestTables.place(helper, 1, 2, 1);
    }
}
