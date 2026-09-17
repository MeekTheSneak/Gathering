package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.story.CardStory;
import dev.gathering.core.story.HowItCame;
import dev.gathering.item.CardItem;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.server.CardGrant;
import dev.gathering.server.CardStories;
import dev.gathering.server.Owed;
import dev.gathering.server.PackWrappers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A card a command made says so, on the card, for as long as the card exists.
 * <p>The owner's rule. A card conjured while collecting was off is a proxy and one conjured by an
 * operator is a grant, and if collecting is switched on afterwards either would otherwise sit in a
 * collection looking exactly like a card somebody opened. So the command writes who ran it and
 * which command into the card's history - straight onto a card, or onto a pack for every card
 * that comes out of it.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpawnedCardGameTest {

    private SpawnedCardGameTest() {
    }

    /** A card handed over by a command carries who ran it and which command, as its first chapter. */
    @GameTest(template = "empty")
    public static void aCardACommandMadeSaysSo(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        UUID id = UUID.randomUUID();
        CardGrant.give(player, metadata(id), true, CardStories.spawnedBy("Operator", "foil"));
        ItemStack given = findCard(player, id);
        String wrong = saysSpawned(given, "Operator", "foil");
        if (wrong != null) {
            helper.fail(wrong);
            return;
        }
        helper.succeed();
    }

    /**
     * Every card out of a pack a command made carries it, not only the one the pack remembers
     * being opened for - and so does the pack itself, put back when an opening fails.
     */
    @GameTest(template = "empty")
    public static void everyCardOutOfACommandsPackSaysSo(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Owed.forget(player.getUUID());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        ItemStack pack = PackItem.of(new PackComponent("tst", "play"));
        CardStories.rememberOnPack(pack, CardStories.spawnedBy("Operator", "pack give"));
        List<CardStory.Chapter> stamps = CardStories.chaptersOnPack(pack);
        if (stamps.size() != 1) {
            helper.fail("a pack a command made carries " + stamps.size() + " chapters to hand on, not one");
            return;
        }

        String token = PackWrappers.hold(player, null, "tst", CardIdentity.ofPrinting(first, false),
                List.of(CardIdentity.ofPrinting(first, false), CardIdentity.ofPrinting(second, false)), stamps);
        if (token == null) {
            helper.fail("fixture: the wrapper could not be written down");
            return;
        }
        PackWrappers.torn(player, token);
        for (UUID card : List.of(first, second)) {
            String wrong = saysSpawned(findCard(player, card), "Operator", "pack give");
            if (wrong != null) {
                helper.fail(wrong);
                return;
            }
        }
        helper.succeed();
    }

    /** Null when this card's first chapter says a command made it, run by this person. */
    private static String saysSpawned(ItemStack card, String who, String command) {
        if (card == null) {
            return "the card never reached the inventory";
        }
        CardStory story = CardStories.storyOf(card);
        CardStory.Chapter first = story.beginning();
        if (first == null || first.how() != HowItCame.SPAWNED) {
            return "a card a command made does not say so: " + story.chapters();
        }
        if (!first.who().equals(who) || !first.what().equals(command)) {
            return "a card a command made names " + first.who() + " and " + first.what()
                    + ", not " + who + " and " + command;
        }
        return null;
    }

    private static ItemStack findCard(ServerPlayer player, UUID printing) {
        for (ItemStack stack : player.getInventory().items) {
            if (CardItem.cardOf(stack).flatMap(card -> card.scryfallId()).map(printing::equals).orElse(false)) {
                return stack;
            }
        }
        return null;
    }

    private static CardMetadata metadata(UUID id) {
        return new CardMetadata(
                id, id, "Something", "{1}", 1.0, "Artifact", "",
                java.util.Set.of(), java.util.Set.of(), List.of(), "normal",
                "tst", "Test Set", "1", Rarity.COMMON,
                false, true, true, false, false, List.of("paper"),
                Map.of(), Map.of(), "https://scryfall.com/card/tst/1");
    }
}
