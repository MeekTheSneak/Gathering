package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.GatheringContent;
import dev.gathering.registry.GatheringComponents;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world checks for the card pipeline's one piece of game state.
 *
 * <p>A card item is a pointer to a printing, and the entire mod is built on that pointer
 * surviving everything the game does to an item stack. These tests run against a real
 * registry in a real server, which is the only place a codec registered wrongly actually
 * shows up.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CardComponentGameTest {

    private static final UUID SOL_RING = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");

    @GameTest(template = "empty")
    public static void componentTypesAreRegistered(GameTestHelper helper) {
        assertRegistered(helper, "card component", GatheringComponents.CARD.get());
        assertRegistered(helper, "deck component", GatheringComponents.DECK.get());

        if (!BuiltInRegistries.ITEM.containsKey(Gathering.id(GatheringContent.CARD_ID))) {
            helper.fail("The card item is not registered");
        }
        if (!BuiltInRegistries.ITEM.containsKey(Gathering.id(GatheringContent.DECK_ID))) {
            helper.fail("The deck item is not registered");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aCardStackCarriesItsPrinting(GameTestHelper helper) {
        ItemStack stack = CardItem.of(CardComponent.of(CardIdentity.ofPrinting(SOL_RING, true)));

        CardComponent card = CardItem.cardOf(stack)
                .orElseThrow(() -> new GameTestAssertException("A freshly built card stack has no card component"));

        if (!card.scryfallId().equals(Optional.of(SOL_RING))) {
            helper.fail("Expected the Sol Ring printing, got " + card.scryfallId());
        }
        if (!card.foil()) {
            helper.fail("The foil flag did not survive being put on the stack");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aCardSurvivesSavingAndLoading(GameTestHelper helper) {
        ItemStack original = CardItem.of(CardComponent.of(CardIdentity.ofPrinting(SOL_RING, true)));

        // The real round trip: through NBT and back, against the server's own registries.
        Tag saved = original.save(helper.getLevel().registryAccess());
        ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(), saved)
                .orElseThrow(() -> new GameTestAssertException("A saved card stack would not load back"));

        CardComponent card = CardItem.cardOf(loaded)
                .orElseThrow(() -> new GameTestAssertException("The card component did not survive a save and load"));

        if (!card.equals(CardItem.cardOf(original).orElseThrow())) {
            helper.fail("A card changed across a save and load: " + card);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeckSurvivesSavingAndLoading(GameTestHelper helper) {
        DeckComponent deck = new DeckComponent(
                "Halana and Tevesh",
                "Two commanders, one bad idea",
                Optional.of(UUID.fromString("00000000-0000-4000-8000-000000000001")),
                List.of(CardComponent.of(CardIdentity.ofPrinting(SOL_RING, true))),
                List.of(CardComponent.of(CardIdentity.ofCustom("myserver:goblin_king", false))),
                List.of());

        ItemStack original = DeckItem.of(deck);
        Tag saved = original.save(helper.getLevel().registryAccess());
        ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(), saved)
                .orElseThrow(() -> new GameTestAssertException("A saved deck stack would not load back"));

        DeckComponent restored = DeckItem.deckOf(loaded)
                .orElseThrow(() -> new GameTestAssertException("The deck component did not survive a save and load"));

        if (!restored.equals(deck)) {
            helper.fail("A deck changed across a save and load: " + restored);
        }
        if (restored.deckSize() != 2) {
            helper.fail("Expected two cards in the deck proper, got " + restored.deckSize());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void grantingACardProducesAReadableStack(GameTestHelper helper) {
        // The path /gathering card takes. Until this works there is no way to hold a card at
        // all, and everything about reading one is unreachable.
        dev.gathering.core.card.CardMetadata card = new dev.gathering.core.card.CardMetadata(
                SOL_RING, SOL_RING, "Sol Ring", "{1}", 1.0, "Artifact", "{T}: Add {C}{C}.",
                java.util.Set.of(), java.util.Set.of(), List.of(), "normal", "ltc", "Commander", "284",
                dev.gathering.core.card.Rarity.UNCOMMON, false, true, true, false, false,
                List.of("paper"), java.util.Map.of(), java.util.Map.of(), "https://scryfall.com/");

        ItemStack stack = dev.gathering.server.CardGrant.stackFor(card, true)
                .orElseThrow(() -> new GameTestAssertException("A granted card produced no stack"));

        CardComponent component = CardItem.cardOf(stack)
                .orElseThrow(() -> new GameTestAssertException("A granted card carries no card component"));

        if (!component.scryfallId().equals(Optional.of(SOL_RING))) {
            helper.fail("A granted card points at the wrong printing: " + component.scryfallId());
        }
        if (!component.foil()) {
            helper.fail("A granted foil card is not foil");
        }
        if (stack.getItem() != GatheringContent.CARD.get()) {
            helper.fail("A granted card is not a card item");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDroppedCardKeepsItsPrinting(GameTestHelper helper) {
        ItemStack stack = CardItem.of(CardComponent.of(CardIdentity.ofPrinting(SOL_RING, false)));

        var item = helper.spawnItem(stack.getItem(), 1, 2, 1);
        item.setItem(stack.copy());

        helper.succeedWhen(() -> {
            CardComponent card = CardItem.cardOf(item.getItem())
                    .orElseThrow(() -> new GameTestAssertException("A dropped card lost its component"));
            if (!card.scryfallId().equals(Optional.of(SOL_RING))) {
                helper.fail("A dropped card changed printing");
            }
        });
    }

    private static void assertRegistered(GameTestHelper helper, String what, Object componentType) {
        if (!BuiltInRegistries.DATA_COMPONENT_TYPE.containsValue(
                (net.minecraft.core.component.DataComponentType<?>) componentType)) {
            helper.fail("The " + what + " type is not in the data component registry");
        }
    }
}
