package dev.gathering.core.booster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Why a real booster is never five commons of one color.
 * <p>A print sheet is a physical grid, and the commons sheet of nearly every set is laid out
 * so that the strip a pack is cut from crosses all five colors. MTGJSON publishes which sheets
 * that is true of - {@code balanceColors} - and this mod read that flag, wrote a note saying
 * it was not reproduced, and drew by weight anyway. Opening a hundred packs of a set gave
 * mono-color packs at a rate no real box does, which is the one thing about a booster a
 * limited player notices immediately.
 * <p>What it does: a slot of five or more taken off a balanced sheet gets one card of each of
 * white, blue, black, red and green first, drawn from that color's own share of the sheet at
 * that share's own weights, and the rest of the slot drawn from the whole sheet as before.
 * That is the shape of the physical cut rather than a rejection loop, so it costs one draw per
 * card and cannot fail to terminate.
 * <p>Only mono-colored cards count toward a column. A gold card is on the sheet and can be
 * drawn like anything else; it is not what makes a pack's blue slot blue, and the real sheets
 * are not laid out as though it were.
 * <p>What is <em>not</em> claimed: that this reproduces a real sheet's exact layout. Wizards
 * does not publish one. It reproduces the property that layout exists for, which is the
 * difference between a pack that feels like a booster and one that does not.
 * <p>Pure.
 */
public final class ColorBalance {

    /** The five columns, in the order they are drawn, so a pack is reproducible from a seed. */
    public static final String COLORS = "WUBRG";

    /**
     * How many cards a slot needs before balancing means anything.
     * <p>Five: one per color. A slot of four cannot hold all five and is drawn plainly, which
     * is also what the physical sheet does - the balanced cut is the long strip.
     */
    public static final int NEEDS_AT_LEAST = COLORS.length();

    private ColorBalance() {
    }

    /**
     * Whether a slot of this size off this sheet can be balanced at all.
     * <p>Everything has to be true at once: the data says the sheet is balanced, the slot is
     * long enough to hold one of each, colors were read for this sheet, and every one of the
     * five columns has something in it. A sheet missing a column is a sheet this cannot
     * balance without inventing a card, so it is drawn plainly.
     */
    public static boolean applies(BoosterSheet sheet, int howMany) {
        if (sheet == null || !sheet.balanced() || sheet.fixed() || howMany < NEEDS_AT_LEAST) {
            return false;
        }
        for (int index = 0; index < COLORS.length(); index++) {
            if (columnOf(sheet, COLORS.charAt(index)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * One color's share of a sheet: the mono-colored cards of that color, at their own weights.
     * <p>A sheet in its own right, so drawing from it is the same weighted draw as drawing
     * from the whole thing - no second code path, and a card printed twice on the sheet is
     * still twice as likely within its column.
     */
    public static BoosterSheet columnOf(BoosterSheet sheet, char color) {
        if (sheet == null) {
            return BoosterSheet.EMPTY;
        }
        Map<UUID, Long> column = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> card : sheet.weights().entrySet()) {
            if (isOnly(sheet.colorOf(card.getKey()), color)) {
                column.put(card.getKey(), card.getValue());
            }
        }
        return column.isEmpty()
                ? BoosterSheet.EMPTY
                : new BoosterSheet(sheet.name() + ":" + color, sheet.foil(), sheet.duplicates(),
                        false, column, false, sheet.colors());
    }

    /** Whether these color letters are exactly this one color and nothing else. */
    private static boolean isOnly(String letters, char color) {
        return letters != null && letters.length() == 1 && letters.charAt(0) == color;
    }

    /**
     * The colors, in draw order, that a balanced slot takes one of each of.
     * <p>Here rather than inline so the order is stated once: it decides what a seed opens,
     * and a pack that changed because somebody reordered a loop is a pack nobody can audit.
     */
    public static List<Character> columnsToFill() {
        List<Character> order = new ArrayList<>(COLORS.length());
        for (int index = 0; index < COLORS.length(); index++) {
            order.add(COLORS.charAt(index));
        }
        return List.copyOf(order);
    }
}
