package dev.gathering.client;

import dev.gathering.core.ui.Rect;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Where a turned card actually lands, for the things drawn around it that cannot turn with it.
 * <p>A glow is an upright rectangle. The card it belongs to is not: dragged aside during a pack
 * opening it slides, drops and leans, and a lean is a foreshortening - the card gets narrower and
 * one edge gets shorter than the other. The glow was drawn against the rectangle the card was asked
 * for, so it stayed level and full width around a card that was neither, and came away from the
 * edge that had turned away.
 * <p>What is checked is the arithmetic behind the fix: the box the card really occupies.
 */
@GameTestHolder("gathering")
@PrefixGameTestTemplate(false)
public final class CardLensBoundsGameTest {

    private static final Rect CARD = new Rect(100, 40, 120, 168);

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @GameTest(template = "empty")
    public static void acardLyingFlatFillsTheBoxItWasGiven(GameTestHelper helper) {
        Rect flat = CardLens.of(CARD, 0f, 0f).bounds();
        require(Math.abs(flat.x() - CARD.x()) <= 1 && Math.abs(flat.y() - CARD.y()) <= 1,
                "a card turned nowhere was not where it was put: " + flat);
        require(Math.abs(flat.width() - CARD.width()) <= 1
                        && Math.abs(flat.height() - CARD.height()) <= 1,
                "a card turned nowhere was not the size it was given: " + flat);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aturnedCardNarrowsAndStaysCentered(GameTestHelper helper) {
        // Both ways, because the pack is dragged both ways and a box that only tracked one of them
        // would look right until somebody swiped the other way.
        for (float yaw : new float[] {-22f, -12f, 12f, 22f}) {
            Rect turned = CardLens.of(CARD, yaw, 0f).bounds();
            require(turned.width() < CARD.width(),
                    "a card turned " + yaw + " degrees was drawn no narrower: " + turned);
            require(turned.width() > CARD.width() / 2,
                    "a card turned " + yaw + " degrees collapsed to " + turned.width());
            // The box leans, and it is meant to: in perspective the edge that came towards the eye
            // is magnified and the edge that went away is shrunk, so a turned card genuinely does
            // not sit centered on the rectangle it was given. What it must not do is walk off it -
            // a light that drifted further from the card the harder it was pulled would be the same
            // fault in a subtler form.
            require(Math.abs(turned.centerX() - CARD.centerX()) < CARD.width() / 8.0,
                    "a card turned " + yaw + " degrees walked sideways: " + turned + " against " + CARD);
            require(Math.abs(turned.centerY() - CARD.centerY()) <= 1,
                    "a card turned " + yaw + " degrees about the level moved up or down: " + turned);
            // Perspective makes the near edge longer than the far one, so the box is taller than
            // the card - never shorter, which would leave the card's corners outside its own light.
            require(turned.height() >= CARD.height(),
                    "a card turned " + yaw + " degrees lost height: " + turned);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theHarderItIsTurnedTheNarrowerItGets(GameTestHelper helper) {
        int wider = CardLens.of(CARD, 8f, 0f).bounds().width();
        int narrower = CardLens.of(CARD, 22f, 0f).bounds().width();
        require(narrower < wider,
                "turning a card further did not narrow it: " + wider + " then " + narrower);
        helper.succeed();
    }
}
