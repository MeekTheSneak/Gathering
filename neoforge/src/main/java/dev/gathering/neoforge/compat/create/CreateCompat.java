package dev.gathering.neoforge.compat.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import dev.gathering.Gathering;
import dev.gathering.item.GatheringContent;
import java.util.List;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Everything this mod does with Create, installed only when Create is.
 * <p>Loaded by name from the entry point after asking whether Create is there, so none of Create's
 * classes are ever touched on a server without it.
 * <p>Display sources, for a Display Link placed against any table: a tournament's standings, its
 * pairings, its round and clock, the match at that table, and the life totals of the game on it.
 * <p>And boosters opened by Deployers: see {@link DeployerPacks}.
 */
public final class CreateCompat {

    private static final DeferredRegister<DisplaySource> SOURCES =
            DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, Gathering.MOD_ID);

    static final Supplier<DisplaySource> STANDINGS = SOURCES.register("tournament_standings", TournamentStandingsSource::new);
    static final Supplier<DisplaySource> PAIRINGS = SOURCES.register("tournament_pairings", TournamentPairingsSource::new);
    static final Supplier<DisplaySource> ROUND = SOURCES.register("tournament_round", TournamentRoundSource::new);
    static final Supplier<DisplaySource> TABLE_MATCH = SOURCES.register("table_match", TableMatchSource::new);
    static final Supplier<DisplaySource> TABLE_LIFE = SOURCES.register("table_life", TableLifeSource::new);

    private CreateCompat() {
    }

    public static void init(IEventBus modBus) {
        SOURCES.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(CreateCompat::attachToTables));
        // An empty-handed Deployer pressing on a booster on a Depot or a belt opens it.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DeployerPacks::onRightClickBlock);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> DeployerPacks.clear());
    }

    /** Every table offers every source: a Display Link against any of its blocks reads that table. */
    private static void attachToTables() {
        List<Supplier<DisplaySource>> sources = List.of(STANDINGS, PAIRINGS, ROUND, TABLE_MATCH, TABLE_LIFE);
        for (var table : GatheringContent.tables()) {
            for (Supplier<DisplaySource> source : sources) {
                DisplaySource.BY_BLOCK.add(table.get(), source.get());
            }
        }
    }
}
