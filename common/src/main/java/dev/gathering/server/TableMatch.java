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
     */
    public static void settleIfFinished(ServerLevel level, BlockPos tableOrigin, GameState state) {
        if (!GameOutcome.isFinished(state)) {
            return;
        }
        TableBlockEntity table = TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> TableBlock.entityAt(level, anchor))
                .orElse(null);
        if (table == null) {
            return;
        }
        MatchState match = table.match().orElse(null);
        if (match == null) {
            return;
        }

        Optional<SeatId> winner = GameOutcome.winnerOf(state);
        MatchState next = winner.map(match::afterGameWonBy).orElseGet(match::afterDrawnGame);
        table.recordMatch(next);

        if (next.hasGameToPlay()) {
            // The board goes away but the decks and the score do not: a set is still running,
            // and the next game starts when somebody crouches on the table.
            table.endGameKeepingMatch();
            TableBroadcast.closeAtTable(level, tableOrigin);
            announce(level, tableOrigin, next, winner);
        } else {
            // The set is over. Everybody gets their deck back, which is the whole reason the
            // table was holding them.
            announce(level, tableOrigin, next, winner);
            TableSessions.returnDecks(level, tableOrigin, table);
            table.endSession();
            TableBroadcast.closeAtTable(level, tableOrigin);
        }
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

    private static void announce(
            ServerLevel level, BlockPos tableOrigin, MatchState match, Optional<SeatId> winner) {
        String key = match.hasGameToPlay()
                ? (match.sideboardingBeforeNextGame()
                        ? "message.gathering.game_over_sideboard"
                        : "message.gathering.game_over_next")
                : "message.gathering.match_over";

        Component line = winner
                .map(seat -> Component.translatable(key, nameOf(level, tableOrigin, seat), match.gameNumber(),
                        match.rules().bestOf()))
                .orElseGet(() -> Component.translatable("message.gathering.game_drawn",
                        match.gameNumber(), match.rules().bestOf()));

        TableBroadcast.tell(level, tableOrigin, line);
    }

    private static Component nameOf(ServerLevel level, BlockPos tableOrigin, SeatId seat) {
        return TableSessions.sessionAt(level, tableOrigin)
                .map(session -> session.state().seatState(seat))
                .map(dev.gathering.SeatNames::of)
                .orElseGet(() -> dev.gathering.SeatNames.of(java.util.Optional.empty()));
    }
}
