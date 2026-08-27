package dev.gathering.server;

import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSessions;
import dev.gathering.core.game.GameState;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.match.GameOutcome;
import dev.gathering.core.match.MatchState;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The bit between one game and the next.
 *
 * <p>A game is a board and a log; a match is a score. When a game finishes, the score moves on
 * and one of three things is true: somebody has taken the match, or there is another game to
 * play and people get to sideboard first, or there is another game and this format has no
 * sideboard so they simply play it.
 *
 * <p>Nothing here starts the next game by itself. Paper does not either: you finish, you talk
 * about it, you change your deck, and then somebody shuffles up. Crouching on the table starts
 * the next one, using the rules the match was already set up with rather than asking again -
 * a set is one set, not three separate decisions about what format this is.
 */
public final class TableMatch {

    private TableMatch() {
    }

    /**
     * Called after every accepted move: settles the game if that move ended it.
     *
     * <p>Here rather than at the end of the game because there is no end of the game to hook -
     * the only thing that finishes one is a player conceding, which arrives like any other
     * move.
     *
     * @return what the table was told, or null if that move did not end anything. The handler
     *         ignores it; a test reads it, because the sentence is the only visible result of
     *         a game ending and it was wrong for months with nothing to notice.
     */
    public static Component settleIfFinished(
            ServerLevel level, BlockPos tableOrigin, GameState state) {
        if (!GameOutcome.isFinished(state)) {
            return null;
        }
        TableBlockEntity table = TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .orElse(null);
        if (table == null) {
            return null;
        }
        MatchState match = table.match().orElse(null);
        if (match == null) {
            return null;
        }

        Optional<SeatId> winner = GameOutcome.winnerOf(state);
        MatchState next = winner.map(match::afterGameWonBy).orElseGet(match::afterDrawnGame);
        table.recordMatch(next);

        // Written before anything is put away, in both branches. The sentence names whoever
        // won and the only place a seat's name lives is the session, so a line built after
        // endGameKeepingMatch() - which is what the first branch used to do - looked up a
        // game that had just been set to null and credited every won game to nobody at all.
        // Built once, up here, so there is no longer an order to get wrong.
        Component line = lineFor(level, tableOrigin, next, winner);

        if (next.hasGameToPlay()) {
            // The board goes away but the decks and the score do not: a set is still running,
            // and the next game starts when somebody crouches on the table.
            table.endGameKeepingMatch();
        } else {
            // The set is over. Everybody gets their deck back, which is the whole reason the
            // table was holding them - and the pot goes to whoever took the match, or back to
            // its owners if it was drawn.
            TableSessions.returnDecks(level, tableOrigin, table);
            // The match's winner, not the last game's. A set can run out of games without
            // anybody reaching the wins it takes - a drawn decider at one game each - and the
            // pot belongs to whoever took the set or to nobody. Handing it to whoever happened
            // to win the final game would pay out a match that was never won.
            TableSessions.settlePot(level, tableOrigin, table, next.winner().orElse(null));
            table.endSession();
        }
        TableBroadcast.tell(level, tableOrigin, line);
        TableBroadcast.closeAtTable(level, tableOrigin);
        return line;
    }

    /**
     * Starts the next game of a set already under way.
     *
     * <p>Keeps the score and the format: this is the next game of that match, not a new one.
     * The decks the table is holding - sideboarded or not - go back down and get shuffled by
     * {@link TableSessions#start}, so players keep their decks between games the way they do
     * in paper.
     */
    public static void startNextGame(ServerLevel level, BlockPos tableOrigin, ServerPlayer asking) {
        MatchState match = TableSessions.matchAt(level, tableOrigin).orElse(null);
        if (match == null || !match.hasGameToPlay()) {
            asking.sendSystemMessage(Component.translatable("message.gathering.session_not_running"));
            return;
        }
        TableSessions.Outcome outcome =
                TableSessions.start(level, tableOrigin, match.rules(), match);
        asking.sendSystemMessage(Component.translatable(outcome.messageKey()));
        if (outcome == TableSessions.Outcome.STARTED) {
            TableBroadcast.sendToTable(level, tableOrigin);
        }
    }

    /**
     * Whether this table is between games of a set it has not finished.
     *
     * <p>The state where the decks are on the table, the score is on the table, and there is
     * no board - which is when sideboarding happens and when crouching means "next game"
     * rather than "what format?".
     */
    public static boolean isBetweenGames(ServerLevel level, BlockPos tableOrigin) {
        return !TableSessions.hasSession(level, tableOrigin)
                && TableSessions.matchAt(level, tableOrigin)
                        .map(MatchState::hasGameToPlay)
                        .orElse(false);
    }

    /** Whether people get to change their decks before the next game here. */
    public static boolean isSideboarding(ServerLevel level, BlockPos tableOrigin) {
        return isBetweenGames(level, tableOrigin)
                && TableSessions.matchAt(level, tableOrigin)
                        .map(MatchState::sideboardingBeforeNextGame)
                        .orElse(false);
    }

    /** What the table is told when a game ends. Reads the seat's name off the live session. */
    public static Component lineFor(
            ServerLevel level, BlockPos tableOrigin, MatchState match, Optional<SeatId> winner) {
        String key = match.hasGameToPlay()
                ? (match.sideboardingBeforeNextGame()
                        ? "message.gathering.game_over_sideboard"
                        : "message.gathering.game_over_next")
                : "message.gathering.match_over";

        // The finished match reports the winner's wins; the games in between report which
        // game just ended. Passing the game number to both told a 2-1 match "3 games of 3",
        // which is a sweep that never happened.
        return winner
                .map(seat -> Component.translatable(key, nameOf(level, tableOrigin, seat),
                        match.hasGameToPlay() ? match.gameNumber() : match.winsFor(seat),
                        match.rules().bestOf()))
                .orElseGet(() -> Component.translatable("message.gathering.game_drawn",
                        match.gameNumber(), match.rules().bestOf()));
    }

    private static Component nameOf(ServerLevel level, BlockPos tableOrigin, SeatId seat) {
        return TableSessions.sessionAt(level, tableOrigin)
                .map(session -> session.state().seatState(seat))
                .map(dev.gathering.SeatNames::of)
                .orElseGet(() -> dev.gathering.SeatNames.of(java.util.Optional.empty()));
    }
}
