package dev.gathering.core.ui;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a board looks like before anybody decides where on a screen to put it.
 * <p>Which cards are piled on which, which are attached to what, and where to find a card by
 * its id. None of that changes between one frame and the next unless the board does, and all
 * of it used to be worked out again every frame - by the seated screen twice, by several of its
 * clicks, and by the board drawn on the block - out of the same view. Some of it was quadratic.
 * <p>So it is worked out once per view and kept with it. A view is a value the server sent,
 * and a new one arrives as a new object, so "the same view" is asked by identity: cheap, and
 * never wrong about a board that changed.
 * <p><b>Where things go is not in here.</b> Rectangles depend on the window, the camera and
 * the card in the air, all of which move without the board changing, so those are still worked
 * out per frame from what this holds. Keeping them here would be a cache that had to know when
 * somebody resized a window.
 * <p>Built only from the view, so it can hold nothing the view did not already show: a
 * face-down card has no id here because it has none in the view.
 */
public final class BoardPresentation {

    /**
     * One seat's battlefield, ready to lay out.
     *
     * @param cards       the permanents, back to front, as the zone keeps them
     * @param spots       where each is counted for piling: its own place, or nothing for a
     *                    card attached to another, which is drawn beside its host instead
     * @param piles       depths, pile sizes and burial, by the same index
     * @param attachments what is attached to each host
     */
    public record Mat(
            SeatId seat,
            List<CardView> cards,
            List<TablePosition> spots,
            TableStacking.Piles piles,
            Map<CardInstanceId, List<CardView>> attachments) {
    }

    private final GameView view;
    private final List<Mat> mats;
    private final Map<CardInstanceId, CardView> byId;

    private BoardPresentation(GameView view, List<Mat> mats, Map<CardInstanceId, CardView> byId) {
        this.view = view;
        this.mats = mats;
        this.byId = byId;
    }

    /** Works it all out for one view. */
    public static BoardPresentation of(GameView view) {
        List<Mat> mats = new ArrayList<>(view.seats().size());
        Map<CardInstanceId, CardView> byId = new HashMap<>();
        for (SeatView seat : view.seats()) {
            List<CardView> cards = seat.zone(Zone.BATTLEFIELD).cards();
            List<TablePosition> spots = spotsIn(cards);
            mats.add(new Mat(seat.seat(), cards, spots,
                    TableStacking.piles(spots), TableAttachments.by(cards)));
        }
        // In the order the whole board lists them, keeping the first, which is what the
        // linear search this replaces answered for an id seen twice.
        for (CardView card : view.allCardViews()) {
            if (card instanceof CardView.Visible visible) {
                byId.putIfAbsent(visible.id(), card);
            }
        }
        return new BoardPresentation(view, List.copyOf(mats), byId);
    }

    /**
     * Where each card counts for piling.
     * <p>An attached card is drawn beside its host, wherever its own spot says it is; counted
     * at that spot it made a pile of one card read "x2" on the seated board. The board on the
     * block counted them anyway, so the same game piled differently in the two views. Both ask
     * this now.
     */
    public static List<TablePosition> spotsIn(List<CardView> cards) {
        List<TablePosition> spots = new ArrayList<>(cards.size());
        for (CardView card : cards) {
            spots.add(card.host().isPresent() ? null : card.placedAt().orElse(null));
        }
        return spots;
    }

    /** The view this was worked out from. */
    public GameView view() {
        return view;
    }

    /** Every seat's battlefield, in seat order. */
    public List<Mat> mats() {
        return mats;
    }

    /** A card anybody looking at this view can see, by its id. */
    public Optional<CardView> card(CardInstanceId id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    /**
     * The last few presentations, so asking twice about one view works it out once.
     * <p>A few rather than one, because one renderer can draw several tables in a frame and a
     * cache of one would be rebuilt for each of them in turn, every frame. Asked by identity,
     * for the reason the class says. Not thread-safe: one per thread that draws.
     */
    public static final class Memo {

        private final BoardPresentation[] kept;
        private int next;

        public Memo(int howMany) {
            kept = new BoardPresentation[Math.max(1, howMany)];
        }

        /** The presentation of this view, worked out only if it has not been already. */
        public BoardPresentation of(GameView view) {
            for (BoardPresentation one : kept) {
                if (one != null && one.view == view) {
                    return one;
                }
            }
            BoardPresentation built = BoardPresentation.of(view);
            kept[next] = built;
            next = (next + 1) % kept.length;
            return built;
        }
    }
}
