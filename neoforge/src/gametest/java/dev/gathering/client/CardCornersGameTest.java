package dev.gathering.client;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The shape a face-down card is drawn in.
 * <p>The two back textures are square and they are the owner's, so the corner is made by what is
 * drawn rather than by what is painted. A face has come rounded all along - the art arrives with
 * its corners already transparent - so a face-down card beside a face-up one was the only square
 * thing on the table.
 * <p>What can be asked without a window is the arithmetic: that the curve is a curve, that it
 * takes the corner off and nothing else, and that a card too small to have a corner keeps all four.
 */
@GameTestHolder("gathering")
@PrefixGameTestTemplate(false)
public final class CardCornersGameTest {

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @GameTest(template = "empty")
    public static void thecurveComesInAndThenStops(GameTestHelper helper) {
        // A card about the size of one on a board, and one filling the window.
        for (int width : new int[] {40, 63, 126, 320}) {
            int radius = CardSleeves.radiusFor(width, width * 88 / 64);
            require(radius >= 1, "a card " + width + " across was too small to round");

            int last = Integer.MAX_VALUE;
            for (int row = 0; row < radius; row++) {
                int inset = CardSleeves.insetAt(radius, row);
                require(inset >= 0, "row " + row + " of a " + width + " card was inset " + inset);
                require(inset <= radius, "row " + row + " bit " + inset + " off a radius of " + radius);
                require(inset <= last, "the curve went back out at row " + row + ": " + last + " then " + inset);
                require(inset * 2 < width, "row " + row + " of a " + width + " card left nothing to draw");
                last = inset;
            }
            // The outermost row is actually cut back, and the innermost meets the card's side
            // squarely. Neither holds and this is a chamfer, or a hairline notch running the
            // length of the card, rather than a corner.
            require(CardSleeves.insetAt(radius, 0) >= 1,
                    "the outermost row of a " + width + " card was not cut back at all");
            require(CardSleeves.insetAt(radius, radius - 1) == 0,
                    "the innermost row of a " + width + " card did not meet the side");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void acardTooSmallToRoundKeepsItsCorners(GameTestHelper helper) {
        // The pile counts and the miniature board draw cards a few pixels across. A corner there
        // is not a corner, it is a missing pixel, and four missing pixels read as a broken sprite.
        require(CardSleeves.radiusFor(6, 8) == 0, "a card six across was rounded anyway");
        require(CardSleeves.radiusFor(24, 33) == 0, "a card whose corner would be one pixel was rounded");
        require(CardSleeves.radiusFor(1, 1) == 0, "a card one pixel across was rounded anyway");
        require(CardSleeves.radiusFor(0, 0) == 0, "a card of no size was rounded anyway");
        // And a card wide but flat - a stack seen edge on - must not have its two curves overlap.
        require(CardSleeves.radiusFor(200, 6) == 0, "a card six tall was given a corner taller than it");
        helper.succeed();
    }
}
