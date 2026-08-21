package dev.gathering.neoforge;

import dev.gathering.Gathering;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.GatheringContent;
import dev.gathering.registry.GatheringComponents;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All NeoForge registration, in one class.
 *
 * <p>Concentrated deliberately: registration order across classes is classloading-dependent
 * and effectively invisible, so keeping it in one place makes the order explicit.
 */
final class GatheringRegistration {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, Gathering.MOD_ID);

    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, Gathering.MOD_ID);

    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, Gathering.MOD_ID);

    private static final Supplier<Item> CARD =
            ITEMS.register(GatheringContent.CARD_ID, GatheringContent::createCard);
    private static final Supplier<Item> DECK =
            ITEMS.register(GatheringContent.DECK_ID, GatheringContent::createDeck);

    private static final Supplier<DataComponentType<CardComponent>> CARD_COMPONENT =
            DATA_COMPONENTS.register(GatheringComponents.CARD_ID, GatheringComponents::createCardType);
    private static final Supplier<DataComponentType<DeckComponent>> DECK_COMPONENT =
            DATA_COMPONENTS.register(GatheringComponents.DECK_ID, GatheringComponents::createDeckType);

    private static final Supplier<CreativeModeTab> TAB = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + Gathering.MOD_ID + ".main"))
            .icon(() -> new ItemStack(DECK.get()))
            .displayItems((parameters, output) -> {
                output.accept(new ItemStack(CARD.get()));
                output.accept(new ItemStack(DECK.get()));
            })
            .build());

    private GatheringRegistration() {
    }

    static void bootstrap(IEventBus modBus) {
        ITEMS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        CREATIVE_TABS.register(modBus);

        // Bound as suppliers, so this runs safely here in the constructor rather than
        // waiting on a lifecycle event: nothing is resolved until something asks.
        GatheringContent.CARD.bind(CARD);
        GatheringContent.DECK.bind(DECK);
        GatheringComponents.CARD.bind(CARD_COMPONENT);
        GatheringComponents.DECK.bind(DECK_COMPONENT);
    }

    static Supplier<CreativeModeTab> creativeTab() {
        return TAB;
    }
}
