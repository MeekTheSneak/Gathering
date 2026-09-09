package dev.gathering.server;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Which server is running, where its save is, and which run this is.
 * <p>Two things needed one home and now have it.
 * <p>The first is <em>where</em>. Everything this mod wrote went under the game directory,
 * which is right for downloaded card metadata - that is the same data whatever world you are
 * in, and downloading it once is the point. It is wrong for anything anybody owns. Two
 * single-player worlds in one installation shared a single file of owed rewards, so a booster
 * interrupted in one world could be claimed in the other and was then gone from the world that
 * owed it. Property belongs to the save it was earned in, so it is written under
 * {@code <save>/gathering} instead.
 * <p>The second is <em>which run</em>. Work handed to the card and collation executors
 * outlives the world that asked for it: in single-player, leaving to the main menu and opening
 * another world happens in one process, and a callback from the first can land while the
 * second is running and publish into it. The generation is stamped when work starts and
 * checked when it completes, so a result belonging to a world that has gone is dropped instead
 * of applied.
 * <p>Server thread only for {@link #started} and {@link #stopped}; {@link #generation} and
 * {@link #isStill} are safe to read from a worker.
 */
public final class ServerRun {

    /** The directory this mod writes into inside a save. */
    private static final String FOLDER = "gathering";

    /** Bumped for every server this process runs, so no two runs share a number. */
    private static final AtomicLong GENERATION = new AtomicLong();

    private static volatile MinecraftServer running;

    private ServerRun() {
    }

    /** Called from both loaders when a server starts, before anything else is warmed. */
    public static void started(MinecraftServer server) {
        GENERATION.incrementAndGet();
        running = server;
    }

    /**
     * Called from both loaders when a server has stopped.
     * <p>The generation moves on here rather than only on the next start, so work that
     * completes between one world closing and the next opening is already out of date when it
     * lands, instead of being current until something else begins.
     */
    public static void stopped() {
        GENERATION.incrementAndGet();
        running = null;
    }

    /** The server that is running, if one is. */
    public static Optional<MinecraftServer> server() {
        return Optional.ofNullable(running);
    }

    /** Which run this is. Stamp work with it; check it before publishing a result. */
    public static long generation() {
        return GENERATION.get();
    }

    /**
     * Whether the run that stamped this is still the run that is going.
     * <p>The one question a completing callback has to ask before it touches anything.
     */
    public static boolean isStill(long generation) {
        return generation == GENERATION.get() && running != null;
    }

    /**
     * Where this save keeps what belongs to it.
     * <p>Empty when no server is running, which is every call from a client and every call
     * from a test that has not stood one up. A caller with nowhere to write does not write,
     * rather than falling back to a shared directory - falling back is the bug this exists to
     * fix.
     */
    public static Optional<Path> saveDirectory() {
        MinecraftServer server = running;
        return server == null
                ? Optional.empty()
                : Optional.of(server.getWorldPath(LevelResource.ROOT).resolve(FOLDER));
    }

    /**
     * Wraps a completion so it does nothing once the world that asked for it has gone.
     * <p>The generation is taken when this is called, which is when the work is started, and
     * checked when the result lands. Anything reading a shared static on the way back has to
     * go through this or through the asking server's own task queue - those are the only two
     * things that know which world a result belongs to.
     */
    public static <T> java.util.function.BiConsumer<T, Throwable> stillThisRun(
            java.util.function.BiConsumer<T, Throwable> then) {
        long asking = generation();
        return (value, failure) -> {
            if (isStill(asking)) {
                then.accept(value, failure);
            }
        };
    }

    /**
     * One of this save's own folders - owed rewards, replays, want lists, broken games.
     * <p>Everything under here belongs to the world it was made in. What stays under the game
     * directory is downloaded card metadata and collation data, which is the same in every
     * world and is cached once on purpose.
     */
    public static Optional<Path> inSave(String folder) {
        return saveDirectory().map(save -> save.resolve(folder));
    }
}
