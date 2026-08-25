package dev.gathering.village;

import dev.gathering.Gathering;
import dev.gathering.service.ServerSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Putting a card shop in the villages.
 *
 * <p>A local game store is a room with a counter, a chest of stock behind it and a table or two
 * to play at, and it belongs where every other shop in Minecraft belongs: in a village, built by
 * the same generator, in the village's own materials. So there is one building per village style
 * and it goes into the same pool the fletcher and the librarian come out of.
 *
 * <p>Which means editing a pool the game has already loaded, because a data pack can only replace
 * one of those whole - and a mod that replaced {@code village/plains/houses} would be a mod that
 * quietly deleted every other mod's village building. Adding to the list is the only version of
 * this that is a good neighbour.
 *
 * <p>Everything about the shop itself is published data: the buildings are structure files and
 * the pool entry that names one is a template pool of this mod's own, so what is added here is
 * read rather than built. What each loader supplies is the one line that reaches the pool's
 * template list, which is private in vanilla and opened by an access transformer on NeoForge and
 * an access widener on Fabric - neither of which is a mixin, and both of which are the declared,
 * supported way to do exactly this.
 */
public final class LocalGameStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** The five villages Minecraft builds, and the shop that belongs in each. */
    private static final List<String> STYLES =
            List.of("plains", "desert", "savanna", "snowy", "taiga");

    private LocalGameStore() {
    }

    /** One village style's house pool, and the shop that can go in it. */
    public record Shop(String village, StructureTemplatePool houses,
            List<StructurePoolElement> buildings) {
    }

    /** How a loader reaches the one list vanilla keeps to itself. */
    @FunctionalInterface
    public interface Shelves {

        /** Puts one building into one pool, this many times over. */
        void add(StructureTemplatePool pool, StructurePoolElement building, int times);
    }

    /**
     * Adds a card shop to every village that has houses.
     *
     * <p>Once, when the server starts, before anything has generated. Never fails a start: a
     * village style whose pool is missing - another mod replaced it, a data pack removed it -
     * is a village with no card shop and a line in the log, not a server that will not come up.
     */
    public static void addToVillages(MinecraftServer server, Shelves shelves) {
        if (server == null || shelves == null) {
            return;
        }
        int weight = ServerSettings.get().collecting().villageShopWeight();
        if (!ServerSettings.get().modes().collectionEnabled() || weight <= 0) {
            // Nothing to sell, or a server that would rather build its own shops.
            return;
        }
        List<String> built = new ArrayList<>();
        for (Shop shop : found(server)) {
            for (StructurePoolElement building : shop.buildings()) {
                shelves.add(shop.houses(), building, weight);
            }
            built.add(shop.village());
        }
        if (!built.isEmpty()) {
            LOGGER.info("A card shop can be built in these villages: {} (weight {})",
                    String.join(", ", built), weight);
        }
    }

    /**
     * Which villages this server could build a card shop in, and what it would build.
     *
     * <p>Apart from the adding, because looking things up is where this goes wrong and it is
     * worth being able to ask the question without changing anything. A village style whose
     * pool is missing - another mod replaced it, a data pack removed it - is left out with a
     * line in the log, never a server that will not come up.
     */
    public static List<Shop> found(MinecraftServer server) {
        Registry<StructureTemplatePool> pools = server == null ? null
                : server.registryAccess().registry(Registries.TEMPLATE_POOL).orElse(null);
        if (pools == null) {
            LOGGER.warn("No template pools on this server, so no village has a card shop");
            return List.of();
        }
        // Its own source of randomness, and only to read a pool's own list back out: the world
        // seed decides where villages go and nothing here should touch it.
        RandomSource reading = RandomSource.create(0L);
        List<Shop> shops = new ArrayList<>();
        for (String style : STYLES) {
            StructureTemplatePool houses = pools.get(ResourceLocation.fromNamespaceAndPath(
                    "minecraft", "village/" + style + "/houses"));
            StructureTemplatePool ours = pools.get(
                    Gathering.id("village/" + style + "_card_shop"));
            if (houses == null || ours == null) {
                LOGGER.info("No {} village card shop: {}", style,
                        houses == null ? "that village has no houses to add one to"
                                : "this mod's own building is missing");
                continue;
            }
            shops.add(new Shop(style, houses, ours.getShuffledTemplates(reading)));
        }
        return List.copyOf(shops);
    }
}
