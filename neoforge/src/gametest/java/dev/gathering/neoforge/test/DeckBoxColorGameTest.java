package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.DeckColors;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The color a deck box is painted, and that it stays painted.
 * <p>The point of the color is that you find your deck by looking at the shelf. Two ways that
 * fails without a word from anything: a box that forgets its color when a card comes out of
 * the deck, and a box whose color never crosses the wire - the second of which would look
 * perfectly right in single player and be white on every server.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeckBoxColorGameTest {

    private DeckBoxColorGameTest() {
    }

    /** A deck with no color is drawn white, which is what the texture already is. */
    @GameTest(template = "empty")
    public static void anUnpaintedDeckIsWhite(GameTestHelper helper) {
        ItemStack stack = DeckItem.of(deck());
        if (DeckItem.tintOf(stack, 0) != 0xFFFFFFFF) {
            helper.fail("a deck with no color of its own is tinted "
                    + Integer.toHexString(DeckItem.tintOf(stack, 0)));
            return;
        }
        helper.succeed();
    }

    /** A painted one is drawn in its own color, and only its first layer is tinted. */
    @GameTest(template = "empty")
    public static void aPaintedDeckIsDrawnInItsColor(GameTestHelper helper) {
        int wanted = DeckColors.pick(7L);
        ItemStack stack = DeckItem.of(deck().colored(wanted));
        if (DeckItem.tintOf(stack, 0) != wanted) {
            helper.fail("the box is tinted " + Integer.toHexString(DeckItem.tintOf(stack, 0))
                    + " and the deck says " + Integer.toHexString(wanted));
            return;
        }
        // A second layer, if the art ever grows one, is the linework and must stay unpainted.
        if (DeckItem.tintOf(stack, 1) != 0xFFFFFFFF) {
            helper.fail("the box tints a layer past the first, so any overlay art would be "
                    + "painted along with the body");
            return;
        }
        helper.succeed();
    }

    /**
     * Editing a deck does not repaint the box.
     * <p>The failure this guards is quiet and maddening: take one card out and your deck is a
     * different color on the shelf, so the one thing you were using to find it moves.
     */
    @GameTest(template = "empty")
    public static void editingADeckKeepsItsBox(GameTestHelper helper) {
        DeckComponent painted = deck().colored(DeckColors.pick(3L));
        DeckComponent renamed = painted.named("Something else");
        if (!renamed.color().equals(painted.color())) {
            helper.fail("renaming a deck repainted its box");
            return;
        }
        DeckComponent shorter = painted
                .withoutOne(DeckComponent.Section.MAINBOARD, painted.entries().get(0))
                .orElse(null);
        if (shorter == null) {
            helper.fail("a card could not be taken out of the test deck");
            return;
        }
        if (!shorter.color().equals(painted.color())) {
            helper.fail("taking a card out repainted the box");
            return;
        }
        helper.succeed();
    }

    /**
     * A painted deck survives the wire.
     * <p>The deck component's stream codec is written out by hand, because composing one
     * stops at six parts and a deck now has seven. Hand-written means the two halves can
     * disagree, and the failure would be invisible in single player: the box would be right
     * on the machine that made it and white, or the item missing, on every other.
     */
    @GameTest(template = "empty")
    public static void aPaintedDeckSurvivesTheWire(GameTestHelper helper) {
        DeckComponent painted = deck().colored(DeckColors.pick(11L));
        net.minecraft.network.RegistryFriendlyByteBuf buffer =
                new net.minecraft.network.RegistryFriendlyByteBuf(
                        io.netty.buffer.Unpooled.buffer(),
                        helper.getLevel().registryAccess());
        try {
            DeckComponent.STREAM_CODEC.encode(buffer, painted);
            DeckComponent back = DeckComponent.STREAM_CODEC.decode(buffer);
            if (!back.equals(painted)) {
                helper.fail("a deck came back off the wire different: " + back
                        + " rather than " + painted);
                return;
            }
            if (buffer.readableBytes() != 0) {
                helper.fail(buffer.readableBytes() + " byte(s) of a deck were written and "
                        + "never read, so the two halves of the codec disagree");
                return;
            }
            helper.succeed();
        } finally {
            buffer.release();
        }
    }

    private static DeckComponent deck() {
        CardComponent card = CardComponent.of(dev.gathering.core.card.CardIdentity.ofPrinting(
                UUID.fromString("00000000-0000-4000-8000-000000000001")));
        return new DeckComponent(
                "Test deck", "", Optional.empty(), List.of(card, card), List.of(), List.of());
    }
}
