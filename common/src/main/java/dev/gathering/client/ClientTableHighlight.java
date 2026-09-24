package dev.gathering.client;

import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;

/**
 * What the cursor is on, for the table in the world to draw a ring around.
 * <p>Hovering and selecting are the screen's business: they come out of a mouse position and a
 * click, and neither of those things exists as far as a block entity renderer is concerned. So
 * the screen works them out, as it always has, and leaves the answer here for the renderer to
 * pick up on its next frame.
 * <p>Filed under one table, the one the screen is showing, and asked for by table. See
 * {@link #table}.
 * <p>One player's own idea about their own screen, never sent anywhere - the same as the
 * selection it carries. Nothing here can be true of anybody else's client.
 * <p>Client-only.
 */
public final class ClientTableHighlight {

    /**
     * The table all of this is about: the position the screen files its board under, which is
     * the one the world renderer is handed for each block it draws ({@link ClientTableState#viewOf}).
     * <p>Seats are numbered from zero at every table and card numbers are counted per game, so
     * two tables side by side both have a seat 0 and both have a card 12. Before this was kept,
     * everything here was true of every table in view: the table next door lit your button,
     * ringed its own card 12 when you hovered yours, lit the mat and the pile you were aiming at,
     * and left its card 12 out altogether while yours was in your hand.
     * <p>Null when nothing has been said, which no position is asked as.
     */
    private static BlockPos table;

    private static CardInstanceId hovered;
    private static List<CardInstanceId> selected = List.of();

    /**
     * The card currently following the cursor, which is drawn on the screen and not on the
     * table.
     * <p>A card being dragged has not moved yet - the server has not been told, and will not
     * be until it lands - so the board still lists it wherever it came from. Drawing it there
     * as well leaves a copy lying on the felt while its twin follows the cursor, which reads
     * as the drag having failed.
     */
    private static CardInstanceId inTheAir;

    /** The zone a card is currently being held over, so it can light up as a target. */
    private static SeatId aimedSeat;
    private static int aimedPile = -1;

    /** Whose mat a card in the air would land on, zone or no zone. */
    private static SeatId landing;

    /** Which button on which mat the cursor is resting on, so it can light up on the block. */
    private static SeatId pointedSeat;
    private static int pointedVerb = -1;

    private ClientTableHighlight() {
    }

    /**
     * Makes this table the one everything here is about.
     * <p>Another table's answers are dropped rather than carried over: each writer below says
     * one part of what the cursor is doing, and a part left over from the last table would be
     * read as this one's.
     */
    private static void about(BlockPos at) {
        BlockPos owner = at == null ? null : at.immutable();
        if (!Objects.equals(owner, table)) {
            clear();
            table = owner;
        }
    }

    /** Whether what is kept here is about this table. */
    private static boolean isAbout(BlockPos at) {
        return at != null && at.equals(table);
    }

    public static void set(BlockPos at, CardInstanceId under, List<CardInstanceId> picked, CardInstanceId held) {
        about(at);
        hovered = under;
        selected = List.copyOf(picked);
        inTheAir = held;
    }

    /** Whether this card is the one in the player's hand rather than on this table. */
    public static boolean isInTheAir(BlockPos at, CardInstanceId card) {
        return card != null && isAbout(at) && card.equals(inTheAir);
    }

    /** Which zone a dragged card would go into, so the table can say so before it is let go. */
    public static void aimAt(BlockPos at, SeatId seat, int pile) {
        about(at);
        aimedSeat = pile < 0 ? null : seat;
        aimedPile = pile;
    }

    /**
     * Whose mat a dragged card would land on, zone or no zone.
     * <p>Separate from the zone it is aimed at, because most of a mat is not a zone and a card
     * let go over bare felt still lands on somebody's side of the table. Without it the board
     * drawn on the block said nothing at all about where a card was going unless the cursor
     * happened to be over one of the four small boxes in the corner.
     */
    public static void landingOn(BlockPos at, SeatId seat) {
        about(at);
        landing = seat;
    }

    /**
     * Which mat button the cursor is on, so the board on the block can light it.
     * <p>The seated board works this out again while it draws, from the same cursor - it has
     * the cursor to hand and the world renderer does not, which is the only reason this is
     * kept rather than asked for.
     */
    public static void pointAtVerb(BlockPos at, SeatId seat, int verb) {
        about(at);
        pointedSeat = verb < 0 ? null : seat;
        pointedVerb = verb;
    }

    public static boolean isPointedAtVerb(BlockPos at, SeatId seat, int verb) {
        return pointedVerb >= 0 && pointedVerb == verb && seat != null && isAbout(at)
                && seat.equals(pointedSeat);
    }

    public static boolean isLandingOn(BlockPos at, SeatId seat) {
        return seat != null && isAbout(at) && seat.equals(landing);
    }

    /** Cleared when the in-world view closes, so a ring never outlives the cursor that made it. */
    public static void clear() {
        table = null;
        hovered = null;
        selected = List.of();
        inTheAir = null;
        aimedSeat = null;
        aimedPile = -1;
        landing = null;
        pointedSeat = null;
        pointedVerb = -1;
    }

    public static boolean isLit(BlockPos at, CardInstanceId card) {
        return card != null && isAbout(at) && (card.equals(hovered) || selected.contains(card));
    }

    /** Whether anything at all is lit on this table. For the scripted harness, which cannot see a ring. */
    static boolean isLitAtAll(BlockPos at) {
        return hovered != null && isAbout(at);
    }

    public static boolean isAimedAt(BlockPos at, SeatId seat, int pile) {
        return aimedPile >= 0 && aimedPile == pile && seat != null && isAbout(at)
                && seat.equals(aimedSeat);
    }
}
