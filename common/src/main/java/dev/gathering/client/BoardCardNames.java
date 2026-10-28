package dev.gathering.client;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.item.CardComponent;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * What to call a card on a row, given only its id.
 * <p>Two panels ask this - the commander tax grid on the counters screen and the commander
 * damage grid on the life screen - and each had its own copy of the walk and its own cache.
 * One is enough: a sentence about a card nobody can see is a sentence to write once.
 * <p><b>Which card an id belongs to is asked once and remembered.</b> It used to be asked every
 * frame by walking every card in every zone of every seat - libraries included, so four hundred
 * cards on a Commander table - to answer a question whose answer cannot change: a card instance
 * is one printing for its whole life. The name itself is still looked up each frame, because
 * that arrives from the cache whenever it arrives.
 * <p>One of these per screen, so the cache goes when the screen does.
 * <p>Client-only.
 */
public final class BoardCardNames {

    /** Which printing each card asked about is, found once - it cannot change. */
    private final Map<CardInstanceId, CardComponent> printings = new HashMap<>();

    /**
     * This card's name, or a stand-in for one this client cannot see.
     * <p>A commander in a hand or a library is a card the visibility rules have quite correctly
     * not sent, so there is no name to give - and the row still has to say something, because
     * the damage it has already dealt is on the board whether or not the card is.
     */
    public Component of(GameView board, CardInstanceId card) {
        CardComponent known = printings.get(card);
        if (known == null) {
            if (board == null) {
                return hidden();
            }
            for (CardView held : board.allCardViews()) {
                if (held instanceof CardView.Visible visible && visible.id().equals(card)) {
                    known = CardComponent.of(visible.identity());
                    printings.put(card, known);
                    break;
                }
            }
            if (known == null) {
                return hidden();
            }
        }
        CardComponent asked = known;
        return ClientCardCache.get().summary(asked)
                .map(summary -> (Component) Component.literal(summary.name()))
                .orElseGet(() -> ClientCardCache.get().unnamed(asked));
    }

    private static Component hidden() {
        return Component.translatable("screen.gathering.card.somewhere_hidden");
    }
}
