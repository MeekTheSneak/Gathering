package dev.gathering.core.card;

import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Which card pictures a viewer still has to be told about.
 *
 * <p>A client only ever asked what a card looks like on behalf of cards in its own inventory,
 * which is the right scope for a request - it grants no access the player did not have - but
 * it leaves somebody else's cards with no picture at all. A public graveyard opens onto empty
 * recesses under a count saying there is something in it.
 *
 * <p>So the server pushes instead, and this decides what. Pure, and here rather than beside
 * the networking, because the one thing that matters about it is a security property rather
 * than a rendering one: <b>every printing named here is one this view already revealed.</b>
 * A card the rules turned into a count or a sleeve has no identity in the view, so there is
 * nothing to name - which is what makes pushing pictures safe where widening what a client
 * may ask for would not have been.
 */
public final class ArtToSend {

    private ArtToSend() {
    }

    /**
     * The printings in this view that this viewer has not already been sent.
     *
     * @param view what was sent to that viewer, which is what bounds the answer
     * @param alreadySent printings they have been told about before, which are skipped
     */
    public static Set<UUID> wanted(GameView view, Set<UUID> alreadySent) {
        Set<UUID> wanted = new LinkedHashSet<>();
        if (view == null) {
            return wanted;
        }
        for (CardView card : view.allCardViews()) {
            // Visible only. Everything else arrived as a count or a sleeve and has no
            // identity to name, which is the whole of the safety argument.
            if (card instanceof CardView.Visible visible) {
                UUID printing = visible.identity().scryfallId();
                // A custom card has no printing and no Scryfall picture to push. Passing its
                // null through instead used to blow up the send on the server thread - the
                // set the sends are remembered in refuses nulls - so one face-up custom card
                // stopped every picture at the table.
                if (printing == null) {
                    continue;
                }
                if (alreadySent == null || !alreadySent.contains(printing)) {
                    wanted.add(printing);
                }
            }
        }
        return wanted;
    }
}
