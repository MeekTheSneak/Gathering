package dev.gathering.neoforge.loot;

import dev.gathering.core.sealed.ArchiveDrops;
import dev.gathering.core.sealed.LootRichness;
import dev.gathering.core.sealed.LootSource;
import dev.gathering.loot.PackLootEntry;
import java.util.Set;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * Sealed product in the loot tables Minecraft only ever rolls from inside another one.
 * <p>{@link PackLootModifier} is a global loot modifier, and NeoForge runs those for the table a
 * roll starts from and for nothing it reaches along the way: {@code LootTable.getRandomItems}
 * calls {@code modifyLoot} with its own id, while a nested table is rolled through
 * {@code getRandomItemsRaw}, which never does. Fishing is exactly that shape. The bobber rolls
 * {@code gameplay/fishing}, which rolls {@code gameplay/fishing/treasure} as a nested entry - so
 * the modifier was asked about the one table it had no rule for and never about the one it
 * had, and on NeoForge nobody ever fished up a pack or an archive pack. Fabric adds a pool to
 * the treasure table itself and always worked.
 * <p>So these tables get what Fabric does: the same entry, added as its own pool while the
 * table loads, rolled wherever the table is reached from. And {@link PackLootModifier} leaves
 * them alone, so a table rolled directly - {@code /loot} does it - is not rolled twice.
 */
public final class NestedLootPools {

    /** Tables vanilla reaches only by nesting, which a global loot modifier cannot see. */
    static final Set<String> NESTED = Set.of("minecraft:gameplay/fishing/treasure");

    private NestedLootPools() {
    }

    public static void onLoad(LootTableLoadEvent event) {
        String table = event.getName().toString();
        if (!NESTED.contains(table)) {
            return;
        }
        LootSource source = LootSource.of(table).orElse(null);
        if (source == null && ArchiveDrops.of(table).isEmpty()) {
            return;
        }
        event.getTable().addPool(LootPool.lootPool()
                .name("gathering_sealed_product")
                .setRolls(ConstantValue.exactly(1.0f))
                .add(PackLootEntry.forTable(source, LootRichness.of(table), table))
                .build());
    }

    /** Whether the global modifier should keep out of this table, because the pool above covers it. */
    public static boolean coveredByAPool(String table) {
        return NESTED.contains(table);
    }
}
