package dev.gathering.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gathering.core.sealed.LootSource;
import dev.gathering.registry.GatheringLoot;
import dev.gathering.server.SealedLoot;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * A loot entry that sometimes produces a sealed pack, and otherwise nothing at all.
 *
 * <p>An entry rather than an item with odds attached, because what drops is not decided by
 * this file: it is decided by the server's config and by what the current set was really
 * sold as, neither of which is known when a loot table is read. Vanilla lets an entry
 * produce zero stacks, which is exactly the shape this needs - a pack that does not come up
 * leaves no gap in the chest.
 *
 * <p>The source is baked in when the entry is made, because the loot table's name is known
 * then and asking again on every roll would be a lookup per chest for an answer that cannot
 * change.
 *
 * <p>Registered on both loaders, so a data pack can put a pack in a chest of its own by
 * writing {@code {"type": "gathering:sealed_product", "source": "structures"}}.
 */
public final class PackLootEntry extends LootPoolSingletonContainer {

    /** A source as a config file names it, so a data pack and the config agree. */
    private static final Codec<LootSource> SOURCE_CODEC = Codec.STRING.comapFlatMap(
            named -> LootSource.named(named)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown loot source: " + named)),
            LootSource::configName);

    public static final MapCodec<PackLootEntry> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(SOURCE_CODEC.fieldOf("source").forGetter(entry -> entry.source))
                    .and(singletonFields(instance))
                    .apply(instance, PackLootEntry::new));

    private final LootSource source;

    private PackLootEntry(LootSource source, int weight, int quality,
            List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.source = source;
    }

    /** An entry for one source, ready to go into a pool. */
    public static LootPoolSingletonContainer.Builder<?> forSource(LootSource source) {
        return simpleBuilder((weight, quality, conditions, functions) ->
                new PackLootEntry(source, weight, quality, conditions, functions));
    }

    @Override
    public LootPoolEntryType getType() {
        return GatheringLoot.SEALED_PRODUCT.get();
    }

    @Override
    public void createItemStack(Consumer<ItemStack> stackConsumer, LootContext lootContext) {
        SealedLoot.rollFrom(source, lootContext.getRandom()).ifPresent(stackConsumer);
    }
}
