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
 * <p>Display sources: against a Scorekeeper's Desk, its tournament - standings, pairings, round and
 * clock, final places, prizes or sign-ups, as the link is set; against a table, the match being
 * played at it and the life totals of its game.
 * <p>And boosters opened by Deployers: see {@link DeployerPacks}.
 */
public final class CreateCompat {

    private static final DeferredRegister<DisplaySource> SOURCES =
            DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, Gathering.MOD_ID);

    static final Supplier<DisplaySource> TOURNAMENT = SOURCES.register("tournament", TournamentDisplaySource::new);
    static final Supplier<DisplaySource> TABLE_MATCH = SOURCES.register("table_match", TableMatchSource::new);
    static final Supplier<DisplaySource> TABLE_LIFE = SOURCES.register("table_life", TableLifeSource::new);

    private CreateCompat() {
    }

    /** The tournament source, for a development scene that links a board by hand. */
    public static DisplaySource tournamentForScenes() {
        return TOURNAMENT.get();
    }

    /** The table's match source, for the same scene. */
    public static DisplaySource tableMatchForScenes() {
        return TABLE_MATCH.get();
    }

    public static void init(IEventBus modBus) {
        SOURCES.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(CreateCompat::attachToTables));
        // An empty-handed Deployer pressing on a booster opens it: lying loose, or on a Depot or belt
        // it faces sideways.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DeployerPacks::onRightClickBlock);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DeployerPacks::onInteractEntity);
        // A Clipboard used on a Scorekeeper's Desk takes down the pairings and the standings.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DeskClipboard::onRightClickBlock);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> DeployerPacks.clear());
    }

    /**
     * A tournament is read off its Scorekeeper's Desk, set to show whichever part of it a board wants.
     * A table offers what is its own: the match being played at it, and the life totals of its game.
     */
    private static void attachToTables() {
        DisplaySource.BY_BLOCK.add(GatheringContent.SCOREKEEPERS_DESK.get(), TOURNAMENT.get());
        for (var table : GatheringContent.tables()) {
            DisplaySource.BY_BLOCK.add(table.get(), TABLE_MATCH.get());
            DisplaySource.BY_BLOCK.add(table.get(), TABLE_LIFE.get());
        }
    }
}
