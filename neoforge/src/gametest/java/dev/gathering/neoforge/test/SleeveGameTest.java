package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.Sleeve;
import dev.gathering.item.DeckComponent;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A deck's sleeves, from the button that picks them to the deck that carries them.
 * <p>The sleeve travels three ways - on the deck item, on the wire when somebody picks one,
 * and in the board view every viewer is sent - and the deck item's codec is written by hand
 * past the point {@code composite} reaches, so the round trip is checked byte for byte.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SleeveGameTest {

    @GameTest(template = "empty")
    public static void aSleevedDeckSurvivesTheWire(GameTestHelper helper) {
        DeckComponent sleeved = deck().sleeved(Sleeve.PICKAXE);

        net.minecraft.network.RegistryFriendlyByteBuf buffer =
                new net.minecraft.network.RegistryFriendlyByteBuf(
                        io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            DeckComponent.STREAM_CODEC.encode(buffer, sleeved);
            DeckComponent back = DeckComponent.STREAM_CODEC.decode(buffer);
            if (back.sleeve() != Sleeve.PICKAXE) {
                helper.fail("a deck came back off the wire in " + back.sleeve() + " sleeves");
                return;
            }
            if (buffer.readableBytes() != 0) {
                helper.fail(buffer.readableBytes() + " byte(s) of a deck were written and never "
                        + "read, so the two halves of the codec disagree");
                return;
            }
            helper.succeed();
        } finally {
            buffer.release();
        }
    }

    @GameTest(template = "empty")
    public static void aDeckKeepsItsSleevesThroughAnEdit(GameTestHelper helper) {
        // The box's color survived edits and the sleeve has to as well: a deck that went back
        // to plain every time a card came out of it would be a deck nobody could sleeve.
        DeckComponent sleeved = deck().sleeved(Sleeve.GRASS).named("Renamed");

        if (sleeved.sleeve() != Sleeve.GRASS) {
            helper.fail("renaming a deck stripped its sleeves");
            return;
        }
        DeckComponent smaller = sleeved
                .withoutOne(DeckComponent.Section.MAINBOARD, sleeved.entries().get(0))
                .orElse(null);
        if (smaller == null || smaller.sleeve() != Sleeve.GRASS) {
            helper.fail("taking a card out stripped the deck's sleeves");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pickingASleeveChangesTheDeckInHand(GameTestHelper helper) {
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = dev.gathering.item.DeckItem.of(deck());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        dev.gathering.server.DeckEdits.sleeve(player,
                dev.gathering.network.SleeveDeckPayload.of(InteractionHand.MAIN_HAND, Sleeve.CYAN));

        DeckComponent after = dev.gathering.item.DeckItem
                .deckOf(player.getItemInHand(InteractionHand.MAIN_HAND)).orElse(null);
        if (after == null || after.sleeve() != Sleeve.CYAN) {
            helper.fail("the deck in hand is sleeved in "
                    + (after == null ? "nothing at all" : after.sleeve().toString()));
            return;
        }

        // And a number nobody should be able to send picks a sleeve that exists rather than
        // throwing: this arrives off a socket.
        dev.gathering.server.DeckEdits.sleeve(player,
                new dev.gathering.network.SleeveDeckPayload(false, Integer.MAX_VALUE));
        DeckComponent nonsense = dev.gathering.item.DeckItem
                .deckOf(player.getItemInHand(InteractionHand.MAIN_HAND)).orElse(null);
        if (nonsense == null || nonsense.sleeve() != Sleeve.DEFAULT) {
            helper.fail("an impossible sleeve number left the deck in "
                    + (nonsense == null ? "nothing" : nonsense.sleeve().toString()));
            return;
        }
        helper.succeed();
    }

    private static DeckComponent deck() {
        dev.gathering.item.CardComponent card = dev.gathering.item.CardComponent.of(
                dev.gathering.core.card.CardIdentity.ofPrinting(
                        java.util.UUID.fromString("00000000-0000-4000-8000-0000000000a1")));
        return new DeckComponent(
                "Sleeved", "", Optional.empty(), List.of(card, card), List.of(), List.of());
    }
}
