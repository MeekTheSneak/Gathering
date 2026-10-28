package dev.gathering.neoforge.data;

import dev.gathering.Gathering;
import dev.gathering.neoforge.loot.PackLootModifier;
import dev.gathering.registry.GatheringLoot;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;

/**
 * The one global loot modifier, generated rather than hand-written.
 * <p>Both files this writes name the modifier by id, and an id typed by hand into the wrong
 * one of them fails silently: the modifier is simply never loaded and packs never drop, with
 * nothing in the log to say why. Generating them from the registered codec removes that
 * failure entirely, and {@code runData} then fails the build if the two ever disagree.
 */
public final class GatheringLootModifiers extends GlobalLootModifierProvider {

    public GatheringLootModifiers(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, Gathering.MOD_ID);
    }

    @Override
    protected void start() {
        // No conditions: which tables a pack may come out of is LootSource's to say, and
        // saying it again here as a list of table ids would be a second copy to drift.
        add(GatheringLoot.SEALED_PRODUCT_ID, new PackLootModifier(new LootItemCondition[0]));
    }
}
