package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A creative player moving a deck keeps its cards.
 * <p>Through the real packet handler and the real network copy: the deck is written the way a
 * client is sent it - cards hidden - and handed back through the creative slot packet, which is
 * exactly what the creative inventory does when a deck is picked up and put down.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeDeckGameTest {

    @GameTest(template = "empty")
    public static void adeckMovedInTheCreativeInventoryKeepsItsCards(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        DeckComponent deck = new DeckComponent("Creative", "", Optional.of(player.getUUID()),
                List.of(card(1), card(2), card(3)), List.of(), List.of(card(4)));
        ItemStack real = DeckItem.of(deck);
        player.getInventory().setItem(0, real);

        var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        ItemStack sent;
        try {
            ItemStack.STREAM_CODEC.encode(buffer, real);
            sent = ItemStack.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
        if (!DeckItem.deckOf(sent).map(DeckComponent::isRedacted).orElse(false)) {
            helper.fail("the client's copy of a deck was not hidden, so this checks nothing");
            return;
        }

        // Picked up from the first hotbar slot, put down in the second: menu slots 36 and 37.
        player.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(36, ItemStack.EMPTY));
        player.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(37, sent));

        DeckComponent landed = DeckItem.deckOf(player.getInventory().getItem(1)).orElse(null);
        if (landed == null || landed.isRedacted() || !landed.entries().equals(deck.entries())
                || !landed.sideboard().equals(deck.sideboard())) {
            helper.fail("moving a deck in the creative inventory replaced its cards: " + landed);
            return;
        }
        helper.succeed();
    }

    private static CardComponent card(int index) {
        return CardComponent.of(CardIdentity.ofPrinting(new UUID(51L, index)));
    }
}
