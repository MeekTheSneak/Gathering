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
        // One list, walked, rather than three named registrations - see GatheringSounds. The
        // two loaders read the same list, which is what keeps them from ending up with
        // different sets of sounds under the same names.
        for (dev.gathering.registry.Registered<net.minecraft.sounds.SoundEvent> sound
                : dev.gathering.sound.GatheringSounds.all()) {
            String id = sound.entryName();
            sound.bindValue(Registry.register(BuiltInRegistries.SOUND_EVENT,
                    Gathering.id(id), dev.gathering.sound.GatheringSounds.create(id)));
        }

        // The block first: the table's item names its block when it is created.
        net.minecraft.world.level.block.Block table = Registry.register(
                BuiltInRegistries.BLOCK, Gathering.id(GatheringContent.TABLE_ID), GatheringContent.createTable());
        GatheringContent.TABLE.bindValue(table);

        GatheringContent.TABLE_ITEM.bindValue(Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.TABLE_ID),
                GatheringContent.createTableItem()));

        // Fabric's own builder, because BlockEntityType.Builder's supplier interface is
        // package-private in vanilla and only NeoForge access-transforms it.
        GatheringContent.TABLE_ENTITY.bindValue(Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Gathering.id(dev.gathering.block.TableBlockEntity.ID),
                net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
                        .create(GatheringContent::createTableEntity, table)
                        .build()));

        Item card = Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.CARD_ID), GatheringContent.createCard());
        Item deck = Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.DECK_ID), GatheringContent.createDeck());

        Item pack = Registry.register(
                BuiltInRegistries.ITEM, Gathering.id(GatheringContent.PACK_ID),
                GatheringContent.createPack());

        GatheringContent.CARD.bindValue(card);
        GatheringContent.DECK.bindValue(deck);
        GatheringContent.PACK.bindValue(pack);

        GatheringComponents.CARD.bindValue(Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Gathering.id(GatheringComponents.CARD_ID),
                GatheringComponents.createCardType()));
        GatheringComponents.DECK.bindValue(Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Gathering.id(GatheringComponents.DECK_ID),
                GatheringComponents.createDeckType()));
        GatheringComponents.POOL.bindValue(Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Gathering.id(GatheringComponents.POOL_ID),
                GatheringComponents.createPoolType()));
        GatheringComponents.PACK.bindValue(Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Gathering.id(GatheringComponents.PACK_ID),
                GatheringComponents.createPackType()));

        Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                Gathering.id("main"),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup." + Gathering.MOD_ID + ".main"))
                        .icon(() -> new ItemStack(deck))
                        .displayItems((parameters, output) -> {
                            output.accept(new ItemStack(card));
                            output.accept(new ItemStack(deck));
                            output.accept(new ItemStack(pack));
                            output.accept(new ItemStack(GatheringContent.TABLE_ITEM.get()));
                        })
                        .build());
    }
}
