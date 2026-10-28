package dev.gathering.fabric.test;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** The Fabric hook on the creative slot packet keeps a moved deck's cards, as NeoForge's does. */
public class FabricCreativeDeckGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void adeckMovedInTheCreativeInventoryKeepsItsCards(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        DeckComponent deck = new DeckComponent("Creative", "", Optional.of(player.getUUID()),
                List.of(CardComponent.of(CardIdentity.ofPrinting(new UUID(52L, 1L)))), List.of(), List.of());
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
        player.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(36, ItemStack.EMPTY));
        player.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(37, sent));
        DeckComponent landed = DeckItem.deckOf(player.getInventory().getItem(1)).orElse(null);
        if (landed == null || landed.isRedacted() || !landed.entries().equals(deck.entries())) {
            helper.fail("moving a deck in the creative inventory replaced its cards: " + landed);
            return;
        }
        helper.succeed();
    }
}
