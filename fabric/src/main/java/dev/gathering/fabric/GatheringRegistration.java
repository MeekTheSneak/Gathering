package dev.gathering.fabric;

import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import dev.gathering.registry.GatheringComponents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * All Fabric registration, in one class, mirroring the NeoForge bootstrap.
 *
 * <p>The registered objects come from the same factories in {@code common}; only the
 * mechanism differs, which is exactly how large the platform-specific surface is meant to
 * be.
 */
final class GatheringRegistration {

    private GatheringRegistration() {
    }

    static void bootstrap() {
        Item card = Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.CARD_ID), GatheringContent.createCard());
        Item deck = Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.DECK_ID), GatheringContent.createDeck());

        GatheringContent.CARD.bindValue(card);
        GatheringContent.DECK.bindValue(deck);

        GatheringComponents.CARD.bindValue(Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Gathering.id(GatheringComponents.CARD_ID),
                GatheringComponents.createCardType()));
        GatheringComponents.DECK.bindValue(Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Gathering.id(GatheringComponents.DECK_ID),
                GatheringComponents.createDeckType()));

        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                Gathering.id("main"),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup." + Gathering.MOD_ID + ".main"))
                        .icon(() -> new ItemStack(deck))
                        .displayItems((parameters, output) -> {
                            output.accept(new ItemStack(card));
                            output.accept(new ItemStack(deck));
                        })
                        .build());
    }
}
