package dev.gathering.neoforge.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gathering.server.SealedLoot;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Sealed product in Minecraft's own loot, on NeoForge.
 *
 * <p>A global loot modifier rather than an added pool, because NeoForge hands a mod the
 * loaded table and not a way to append to it. Being global is the better shape anyway: it
 * sees every table the game rolls, including ones added by a later Minecraft version, so
 * the list of chests this covers is {@link dev.gathering.core.sealed.LootSource}'s to
 * decide and nothing here needs to be kept up to date.
 *
 * <p>No conditions in the json. Which tables matter is a rule, not a setting, and writing
 * it as a hundred {@code loot_table_id} conditions would be a hundred places for it to
 * drift from the one place that already says it.
 *
 * <p>Fabric does the same job with {@link dev.gathering.loot.PackLootEntry}; both ask
 * {@link SealedLoot} the same question and get the same odds.
 */
public final class PackLootModifier extends LootModifier {

    public static final MapCodec<PackLootModifier> CODEC = RecordCodecBuilder.mapCodec(
            instance -> codecStart(instance).apply(instance, PackLootModifier::new));

    public PackLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(
            ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        SealedLoot.rollFor(context.getQueriedLootTableId().toString(), context.getRandom())
                .ifPresent(generatedLoot::add);
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier> codec() {
        return CODEC;
    }
}
