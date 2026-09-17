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

    /**
     * A card put into a deck in the creative inventory is in the deck afterwards, and still exists.
     * <p>The creative inventory does its clicks on the client and sends the slots afterwards, and the
     * copy the player is holding has the deck's own cards hidden - so the server restored its own deck
     * over the client's, dropping the card that had just gone in, while the slot the card came from
     * arrived empty in the same breath. Putting a card into a deck destroyed the card.
     */
    @GameTest(template = "empty")
    public static void acardPutIntoaDeckInCreativeSurvives(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        DeckComponent deck = new DeckComponent("Creative", "", Optional.of(player.getUUID()),
                List.of(card(1), card(2)), List.of(), List.of());
        ItemStack real = DeckItem.of(deck);
        player.getInventory().setItem(0, real);
        CardComponent putIn = card(9);
        player.getInventory().setItem(1, dev.gathering.item.CardItem.of(putIn));

        // What the client holds: the deck with its cards hidden, and the card added to it the way the
        // deck item's own right-click does.
        ItemStack held = asAClientSeesIt(helper, real);
        DeckComponent hidden = DeckItem.deckOf(held).orElseThrow();
        List<CardComponent> withTheCard = new java.util.ArrayList<>(hidden.entries());
        withTheCard.add(putIn);
        held.set(dev.gathering.registry.GatheringComponents.DECK.get(),
                new DeckComponent(hidden.name(), hidden.description(), hidden.owner(), withTheCard,
                        hidden.commanders(), hidden.sideboard(), hidden.color(), hidden.sleeve(), hidden.stories()));

        // And what the creative inventory then sends: the card's slot empty, the deck's slot as held.
        player.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(37, ItemStack.EMPTY));
        player.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(36, held));

        DeckComponent landed = DeckItem.deckOf(player.getInventory().getItem(0)).orElse(null);
        if (landed == null || landed.isRedacted()) {
            helper.fail("the deck came back hidden: " + landed);
            return;
        }
        if (!landed.entries().contains(putIn)) {
            helper.fail("the card put into the deck is not in it: " + landed.entries());
            return;
        }
        if (!landed.entries().containsAll(deck.entries())) {
            helper.fail("putting a card in lost the cards the deck already had: " + landed.entries());
            return;
        }
        helper.succeed();
    }

    /** One stack through the network codec, which is how a client comes by its copy. */
    private static ItemStack asAClientSeesIt(GameTestHelper helper, ItemStack stack) {
        var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, stack);
            return ItemStack.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static CardComponent card(int index) {
        return CardComponent.of(CardIdentity.ofPrinting(new UUID(51L, index)));
    }
}
