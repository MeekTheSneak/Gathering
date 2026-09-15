package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.server.DeckVault;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A deck whose cards arrive hidden - the copy every client is sent, which the creative menu sends back
 * - is given its real cards again, never kept as a deck of stand-ins.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeckVaultGameTest {

    private static final CardComponent BOLT = CardComponent.of(
            dev.gathering.core.card.CardIdentity.ofPrinting(new UUID(7L, 1L), false));
    private static final CardComponent BEARS = CardComponent.of(
            dev.gathering.core.card.CardIdentity.ofPrinting(new UUID(7L, 2L), false));

    /** The copy of a deck a client holds: what it is sent, sent back. */
    private static DeckComponent asAClientHasIt(GameTestHelper helper, DeckComponent deck) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            DeckComponent.PUBLIC_STREAM_CODEC.encode(buffer, deck);
            return DeckComponent.PUBLIC_STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    @GameTest(template = "empty")
    public static void aDeckMovedOnTheCreativeMenuKeepsItsCards(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        DeckComponent real = new DeckComponent("Mine", "", Optional.of(player.getUUID()),
                List.of(BOLT, BEARS), List.of(), List.of());
        ItemStack stack = DeckItem.of(real);
        player.getInventory().setItem(3, stack);
        stack.inventoryTick(helper.getLevel(), player, 3, false);

        stack.set(GatheringComponents.DECK.get(), asAClientHasIt(helper, real));
        if (!DeckItem.deckOf(stack).orElseThrow().isRedacted()) {
            helper.fail("fixture: the client's copy of a deck came back with its cards");
            return;
        }
        stack.inventoryTick(helper.getLevel(), player, 3, false);
        if (!DeckItem.deckOf(stack).orElseThrow().entries().equals(List.of(BOLT, BEARS))) {
            helper.fail("a deck that came back from the creative menu holds " + DeckItem.deckOf(stack).orElseThrow().entries());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeckMadeOnTheCreativeMenuHasTheCardsItWasMadeFrom(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        // What the client makes of two cards right-clicked together, and says went in.
        ItemStack made = DeckItem.of(new DeckComponent("", "", Optional.of(player.getUUID()),
                List.of(BOLT, BEARS), List.of(), List.of()));
        UUID handle = DeckItem.handleOf(made).orElseThrow();
        DeckVault.cardsWentIn(handle, player.getUUID(), List.of(BOLT, BEARS));
        // And what reaches the server of the deck itself.
        made.set(GatheringComponents.DECK.get(), asAClientHasIt(helper, DeckItem.deckOf(made).orElseThrow()));
        player.getInventory().setItem(4, made);
        made.inventoryTick(helper.getLevel(), player, 4, false);
        if (!DeckItem.deckOf(made).orElseThrow().entries().equals(List.of(BOLT, BEARS))) {
            helper.fail("a deck made of two cards on the creative menu holds " + DeckItem.deckOf(made).orElseThrow().entries());
            return;
        }
        helper.succeed();
    }
}
