package dev.gathering.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gathering.core.sealed.LootRichness;
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
 * <p>The source and how good the chest is are both baked in when the entry is made, because
 * the loot table's name is known then and asking again on every roll would be a lookup per
 * chest for an answer that cannot change.
 *
 * <p>The source is optional, and leaving it out means something different rather than nothing:
 * a chest whose whole purpose is to hold packs. A card shop's stock chest is not a lucky find
 * and should not be rolled against one-in-eight odds, and it is not one of the world's sources
 * a server owner switches on and off - it is part of the shop. So an entry with no source
 * always produces a pack, and the pool's own roll count decides how many.
 *
 * <p>Registered on both loaders, so a data pack can put a pack in a chest of its own by
 * writing {@code {"type": "gathering:sealed_product", "source": "structures"}}, and say it is
 * a find worth making with {@code "richness": "rich"}.
 */
public final class PackLootEntry extends LootPoolSingletonContainer {

    /** A source as a config file names it, so a data pack and the config agree. */
    private static final Codec<LootSource> SOURCE_CODEC = Codec.STRING.comapFlatMap(
            named -> LootSource.named(named)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown loot source: " + named)),
            LootSource::configName);

    /**
     * How good the chest is, if a data pack wants to say.
     *
     * <p>Optional and plain by default: a data pack putting packs in a chest of its own is
     * describing an ordinary find unless it says otherwise, which is the safe direction.
     */
    private static final Codec<LootRichness> RICHNESS_CODEC =
            Codec.STRING.xmap(
                    named -> "rich".equalsIgnoreCase(named) ? LootRichness.RICH : LootRichness.PLAIN,
                    richness -> richness.isRich() ? "rich" : "plain");

    public static final MapCodec<PackLootEntry> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            SOURCE_CODEC.optionalFieldOf("source")
                                    .forGetter(entry -> java.util.Optional.ofNullable(entry.source)),
                            RICHNESS_CODEC.optionalFieldOf("richness", LootRichness.PLAIN)
                                    .forGetter(entry -> entry.richness))
                    .and(singletonFields(instance))
                    .apply(instance, PackLootEntry::new));

    private final LootSource source;
    private final LootRichness richness;

    private PackLootEntry(java.util.Optional<LootSource> source, LootRichness richness,
            int weight, int quality, List<LootItemCondition> conditions,
            List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.source = source == null ? null : source.orElse(null);
        this.richness = richness == null ? LootRichness.PLAIN : richness;
    }

    /** An entry for one chest, ready to go into a pool. */
    public static LootPoolSingletonContainer.Builder<?> forTable(
            LootSource source, LootRichness richness) {
        return simpleBuilder((weight, quality, conditions, functions) -> new PackLootEntry(
                java.util.Optional.ofNullable(source), richness, weight, quality,
                conditions, functions));
    }

    @Override
    public LootPoolEntryType getType() {
        return GatheringLoot.SEALED_PRODUCT.get();
    }

    @Override
    public void createItemStack(Consumer<ItemStack> stackConsumer, LootContext lootContext) {
        (source == null
                ? SealedLoot.packFrom(richness, lootContext.getRandom())
                : SealedLoot.rollFrom(source, richness, lootContext.getRandom()))
                .ifPresent(stackConsumer);
    }
}
