package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.BuildDeckPayload;
import dev.gathering.server.CollectionView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Finishing a deck in the builder.
 *
 * <p>The client says which printings it wants and the server is the one that decides whether
 * the box had them. These check that: a deck comes out holding what was really there, the box
 * is that many cards lighter, and a client asking for cards nobody owns gets a deck without
 * them rather than a deck the collection never had.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeckBuilderGameTest {

    private static final UUID SOL_RING = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");
    private static final UUID BOLT = UUID.fromString("11bf83bb-c95b-4b4f-9a56-ce7a1816307a");
    private static final UUID NEVER_OWNED = UUID.fromString("00000000-0000-4000-8000-000000000123");

    @GameTest(template = "empty")
    public static void aBuiltDeckComesOutOfTheBox(GameTestHelper helper) {
        BlockPos where = collection(helper);
        ServerPlayer player = standing(helper, where);
        CollectionBlockEntity box = boxAt(helper, where);
        // Claimed, the way placing one claims it. A collection nobody owns lets nobody take
        // anything out of it, which is the right default and not the case being tested here.
        box.claimFor(player.getUUID());
        box.put(CardIdentity.ofPrinting(SOL_RING, false), 2);
        box.put(CardIdentity.ofPrinting(BOLT, false), 1);

        CollectionView.build(player, new BuildDeckPayload(where, "Test Deck", "",
                List.of(card(SOL_RING), card(SOL_RING)),
                Optional.of(card(BOLT)), dev.gathering.core.card.Sleeve.RED));

        DeckComponent deck = deckInInventory(player);
        if (deck == null) {
            helper.fail("finishing a build handed over no deck");
            return;
        }
        if (deck.entries().size() != 2 || deck.commanders().size() != 1) {
            helper.fail("the deck came out with " + deck.entries().size()
                    + " cards and " + deck.commanders().size() + " commanders");
            return;
        }
        if (!"Test Deck".equals(deck.name())) {
            helper.fail("the deck was not named what the builder called it");
            return;
        }
        // And the box is exactly that many cards lighter.
        if (box.cards().of(CardIdentity.ofPrinting(SOL_RING, false)) != 0
                || box.cards().of(CardIdentity.ofPrinting(BOLT, false)) != 0) {
            helper.fail("the collection still holds cards the deck took out of it");
            return;
        }
        helper.succeed();
    }

    /**
     * A client asking for what the box does not have gets a deck without it.
     *
     * <p>The whole reason the server takes the cards itself rather than believing a list. A
     * deck built out of cards nobody owns is a deck conjured from a packet.
     */
    @GameTest(template = "empty")
    public static void cardsTheBoxDoesNotHaveAreNotConjured(GameTestHelper helper) {
        BlockPos where = collection(helper);
        ServerPlayer player = standing(helper, where);
        CollectionBlockEntity box = boxAt(helper, where);
        box.claimFor(player.getUUID());
        box.put(CardIdentity.ofPrinting(SOL_RING, false), 1);

        CollectionView.build(player, new BuildDeckPayload(where, "Wishful", "",
                List.of(card(SOL_RING), card(NEVER_OWNED), card(NEVER_OWNED)),
                Optional.empty(), dev.gathering.core.card.Sleeve.DEFAULT));

        DeckComponent deck = deckInInventory(player);
        if (deck == null) {
            helper.fail("finishing a build handed over no deck at all");
            return;
        }
        if (deck.entries().size() != 1) {
            helper.fail("a deck asked for three cards out of a box holding one came out with "
                    + deck.entries().size());
            return;
        }
        helper.succeed();
    }

    private static CardComponent card(UUID printing) {
        return CardComponent.of(CardIdentity.ofPrinting(printing, false));
    }

    private static DeckComponent deckInInventory(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            DeckComponent deck = DeckItem.deckOf(stack).orElse(null);
            if (deck != null) {
                return deck;
            }
        }
        return null;
    }

    private static CollectionBlockEntity boxAt(GameTestHelper helper, BlockPos where) {
        return (CollectionBlockEntity) helper.getLevel().getBlockEntity(where);
    }

    private static BlockPos collection(GameTestHelper helper) {
        BlockPos where = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlock(
                where, GatheringContent.COLLECTION.get().defaultBlockState(), 3);
        return where;
    }

    /** Standing at it, because reading a collection needs the player to be in front of one. */
    private static ServerPlayer standing(GameTestHelper helper, BlockPos where) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atCenterOf(where));
        return player;
    }
}
