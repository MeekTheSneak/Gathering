package dev.gathering.client;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.event.CardRef;
import dev.gathering.core.game.event.LogArg;
import dev.gathering.core.game.event.LogEntry;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.item.CardComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Turning a log line into a sentence.
 *
 * <p>The log arrives as a key and a list of typed arguments precisely so that this is the only
 * place that decides what language anybody reads, what a seat is called, and what a card is
 * named. Everything above this has stayed language-free.
 *
 * <p><b>Card names are resolved through the reader's own view.</b> That is the whole security
 * design of the log made concrete: the server already decided how strongly each line may point
 * at a card, and this can only turn that pointer into a name using cards this client was
 * actually sent. A line about a card in somebody's hand has nothing to resolve and says "a
 * card" - to its author as much as to anybody else.
 *
 * <p>Client-only.
 */
public final class GameLogText {

    private GameLogText() {
    }

    /**
     * One line, ready to draw.
     *
     * <p>A rewound line is struck through rather than dropped. A log that quietly loses
     * entries when somebody undoes something is not a record of what happened.
     */
    public static Component render(GameView board, LogEntry entry) {
        List<Object> resolved = new ArrayList<>(entry.args().size());
        for (LogArg arg : entry.args()) {
            resolved.add(resolve(board, arg));
        }
        MutableComponent line = Component.translatable(keyFor(entry), resolved.toArray());
        return entry.undone()
                ? line.withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.DARK_GRAY)
                : line;
    }

    /**
     * The line to read, which is a different sentence when it is about the player's own board.
     *
     * <p>Almost everything anybody does in a two-player game is done to their own side of the
     * table, and every line is written with an actor and a subject - so the log read "Dev drew
     * 7 cards for Dev", "Dev shuffled Dev's library", over and over. A line that names the
     * same player twice is a line nobody wrote by hand.
     *
     * <p>So a key whose actor and subject are the same seat looks for a second wording under
     * the same key with {@code .own} on the end, and uses it if the language file has one.
     * Opting a line in is adding a string; nothing here has to know which lines have one.
     */
    private static String keyFor(LogEntry entry) {
        if (!aboutTheirOwn(entry)) {
            return entry.key();
        }
        String own = entry.key() + ".own";
        return Language.getInstance().has(own) ? own : entry.key();
    }

    /** Whether the first seat named in the line is also named again later. */
    private static boolean aboutTheirOwn(LogEntry entry) {
        SeatId actor = null;
        for (LogArg arg : entry.args()) {
            if (!(arg instanceof LogArg.Seat seat)) {
                continue;
            }
            if (actor == null) {
                actor = seat.seat();
            } else if (actor.equals(seat.seat())) {
                return true;
            }
        }
        return false;
    }

    private static Component resolve(GameView board, LogArg arg) {
        return switch (arg) {
            case LogArg.Seat seat -> nameOfSeat(board, seat.seat());
            case LogArg.Amount amount -> Component.literal(Integer.toString(amount.value()));
            case LogArg.Text text -> Component.literal(text.text());
            // A zone's own key is its title - it heads the screen that spreads the pile out -
            // and a title dropped into the middle of a sentence reads as a proper noun: "moved
            // Island to their Battlefield". The log has its own, lower case.
            case LogArg.Where where -> Component.translatable(
                    "log.gathering.zone." + where.zone().name().toLowerCase(Locale.ROOT));
            case LogArg.Card card -> nameOfCard(board, card.card());
        };
    }

    private static Component nameOfSeat(GameView board, SeatId seat) {
        // Whose board, not who is in the chair. A log line is a record of something that
        // already happened, so somebody standing up must not turn everything they did into
        // "(empty) drew a card".
        return dev.gathering.SeatNames.of(board, seat);
    }

    /**
     * What to call a card the log has pointed at.
     *
     * <p>Three answers, and which one is available was decided by the server against the board
     * at the time. A named card is one this client was sent and can therefore already read; a
     * marker is the same opaque token opponents see on a face-down permanent; and anonymous is
     * a card in a hidden zone, which is a card and nothing more.
     */
    private static Component nameOfCard(GameView board, CardRef ref) {
        return switch (ref) {
            case CardRef.ById byId -> named(board, byId.id());
            case CardRef.ByMarker ignored -> Component.translatable("log.gathering.a_face_down_card");
            case CardRef.Anonymous ignored -> Component.translatable("log.gathering.a_card");
        };
    }

    private static Component named(GameView board, CardInstanceId id) {
        for (CardView card : board.allCardViews()) {
            if (!(card instanceof CardView.Visible visible) || !visible.id().equals(id)) {
                continue;
            }
            return ClientCardCache.get().summary(CardComponent.of(visible.identity()))
                    .<Component>map(summary -> Component.literal(summary.name()))
                    .orElseGet(() -> Component.translatable("screen.gathering.deck.loading_card"));
        }
        // Pointed at a card this client has not been sent, or one that has since left the
        // board. Saying "a card" is both true and the safe direction to be wrong in.
        return Component.translatable("log.gathering.a_card");
    }
}
