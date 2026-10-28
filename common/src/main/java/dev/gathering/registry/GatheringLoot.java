package dev.gathering.registry;

import dev.gathering.loot.PackLootEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;

/**
 * The mod's loot types, bound by the loader bootstrap.
 * <p>One: the entry that sometimes produces a sealed pack. It is registered on both loaders
 * even though only one of them adds it to Minecraft's own tables, because a registered type
 * is what lets a data pack write {@code gathering:sealed_product} into a table of its own,
 * and a type that exists on one loader and not the other is a data pack that works in one
 * place and is a red error in the other.
 */
public final class GatheringLoot {

    public static final String SEALED_PRODUCT_ID = "sealed_product";

    public static final Registered<LootPoolEntryType> SEALED_PRODUCT =
            new Registered<>(SEALED_PRODUCT_ID);

    private GatheringLoot() {
    }

    public static LootPoolEntryType createSealedProductType() {
        return new LootPoolEntryType(PackLootEntry.CODEC);
    }
}
