package dev.gathering.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gathering.core.reward.RewardDefinition;
import dev.gathering.platform.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The rewards a pack author has defined, as one snapshot at a time.
 * <p>A folder of small JSON files beside the server config, read with the game's own codecs,
 * and swapped in whole. It follows {@code LoanerDecks} rather than inventing a second way to
 * do the same thing: operators already know where that folder is and already have a command
 * that re-reads it without a restart.
 *
 * <p>Three properties, each of which is a way a reload could leave a server worse off:
 *
 * <ul>
 *   <li><b>The snapshot is atomic.</b> What is held is one immutable map, built beside the
 *       live one and swapped at the end. Nothing ever sees half a reload, and a grant that
 *       arrives while somebody is editing files gets the whole of the last good set rather
 *       than whatever had been parsed so far.
 *   <li><b>A bad reload changes nothing.</b> If the folder will not read, the previous
 *       snapshot stays exactly as it was. The alternative - an empty set of rewards because
 *       somebody left a trailing comma in one file - turns a typo into a server where nothing
 *       is granted and nobody knows why.
 *   <li><b>A missing dependency is not an error.</b> A reward naming a mod nobody installed
 *       loads and sits there inert. One pack ships rewards for four boss mods and expects two
 *       to be installed; refusing would punish the packs being careful.
 * </ul>
 *
 * <p>Nothing here fetches anything. The codec accepts a set, a product, a color, a count and
 * a list of mod ids, and {@link RewardDefinition} refuses any of them that looks like an
 * address - so a reward file cannot become a way to make a server open a connection.
 * <p>Server thread only for reload; reads are safe from anywhere.
 */
public final class Rewards {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("Gathering");

    /** Where a pack drops its reward files. Beside the loaner shelf, for the same reason. */
    public static final String DIRECTORY = "gathering-rewards";

    /** A ceiling, so a folder somebody pointed at a share does not become the whole heap. */
    private static final int MOST_REWARDS = 512;

    /** The biggest one file may be. A reward is a handful of short fields. */
    private static final long BIGGEST_FILE = 64 * 1024;

    /**
     * The contract, as the game's own codec system describes it.
     * <p>Built with {@code RecordCodecBuilder} rather than read field by field out of a
     * {@code JsonObject}, so the shape is checked by the same machinery every vanilla data
     * file goes through and an error names the field it is about.
     */
    public static final Codec<RewardDefinition> CODEC = RecordCodecBuilder.create(each -> each.group(
            Codec.STRING.fieldOf("id").forGetter(RewardDefinition::id),
            Codec.STRING.fieldOf("set").forGetter(RewardDefinition::set),
            Codec.STRING.fieldOf("product").forGetter(RewardDefinition::product),
            Codec.STRING.optionalFieldOf("color").forGetter(RewardDefinition::color),
            Codec.INT.optionalFieldOf("count", 1).forGetter(RewardDefinition::count),
            Codec.STRING.listOf().optionalFieldOf("required_mods", List.of())
                    .forGetter(RewardDefinition::requiredMods)
    ).apply(each, RewardDefinition::new));

    /**
     * Everything loaded, by id. Replaced whole, never edited in place.
     * <p>Volatile because it is written on whichever thread ran the reload and read on
     * whichever thread is handing somebody a pack.
     */
    private static volatile Map<String, RewardDefinition> loaded = Map.of();

    /** What the last reload complained about, for the operator who asked for it. */
    private static volatile List<String> lastProblems = List.of();

    private Rewards() {
    }

    /** Every reward that loaded, whether or not its mods are installed. */
    public static Map<String, RewardDefinition> all() {
        return loaded;
    }

    /** What the last reload had to say about the files it read. */
    public static List<String> problems() {
        return lastProblems;
    }

    /**
     * One reward by name, if it loaded and everything it needs is installed.
     * <p>The mod check happens here rather than at load, so that installing a mod and
     * restarting is enough to bring its rewards to life without anybody re-reading files.
     */
    public static Optional<RewardDefinition> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        RewardDefinition found = loaded.get(id.strip().toLowerCase(Locale.ROOT));
        if (found == null || !found.appliesWith(Rewards::isInstalled)) {
            return Optional.empty();
        }
        return Optional.of(found);
    }

    /**
     * Whether a mod is present.
     * <p>Asked of the loader, which is the only thing that knows. A loader that cannot answer
     * says no, which holds a reward back rather than handing out something whose dependency
     * may be absent - the safe direction.
     */
    private static boolean isInstalled(String modId) {
        try {
            return Platform.get().isModLoaded(modId);
        } catch (RuntimeException cannotTell) {
            return false;
        }
    }

    /** Between servers: one world's reward files are not the next one's. */
    public static void clear() {
        loaded = Map.of();
        lastProblems = List.of();
    }

    /**
     * Reads the folder again, and keeps what was there if it cannot.
     *
     * @return how many rewards are loaded afterwards
     */
    public static int reload() {
        Path folder = Platform.get().configDirectory().resolve(DIRECTORY);
        if (!Files.isDirectory(folder)) {
            // Not an error and not a reason to empty the shelf: a server with no reward files
            // is the ordinary case, and one whose folder vanished mid-session keeps what it
            // read rather than silently stopping.
            if (loaded.isEmpty()) {
                lastProblems = List.of();
            }
            return loaded.size();
        }

        Map<String, RewardDefinition> built = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        try (Stream<Path> listing = Files.list(folder)) {
            List<Path> files = listing
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                if (built.size() >= MOST_REWARDS) {
                    problems.add("Stopped after " + MOST_REWARDS + " rewards.");
                    break;
                }
                readInto(built, problems, file);
            }
        } catch (IOException couldNotList) {
            // The folder itself is unreadable. Keeping the previous snapshot is the whole
            // point: a permissions change must not become a server that grants nothing.
            problems.add("Could not read " + DIRECTORY + ": " + couldNotList.getMessage());
            lastProblems = List.copyOf(problems);
            LOGGER.warn("Could not read the rewards folder; keeping the {} already loaded",
                    loaded.size());
            return loaded.size();
        }

        loaded = Map.copyOf(built);
        lastProblems = List.copyOf(problems);
        LOGGER.info("Loaded {} reward definition(s) with {} problem(s)",
                built.size(), problems.size());
        return built.size();
    }

    /** One file, named in anything it has to complain about. */
    private static void readInto(
            Map<String, RewardDefinition> built, List<String> problems, Path file) {
        String named = file.getFileName().toString();
        try {
            if (Files.size(file) > BIGGEST_FILE) {
                problems.add(named + ": larger than " + BIGGEST_FILE + " bytes");
                return;
            }
            JsonElement json = JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8));
            DataResult<RewardDefinition> read = CODEC.parse(JsonOps.INSTANCE, json);
            RewardDefinition definition = read.result().orElse(null);
            if (definition == null) {
                problems.add(named + ": " + read.error()
                        .map(DataResult.Error::message)
                        .orElse("could not be read"));
                return;
            }
            List<RewardDefinition.Problem> wrong = definition.problems();
            if (!wrong.isEmpty()) {
                for (RewardDefinition.Problem problem : wrong) {
                    problems.add(named + ": " + problem);
                }
                return;
            }
            String id = definition.id().strip().toLowerCase(Locale.ROOT);
            if (built.containsKey(id)) {
                problems.add(named + ": a reward called '" + id + "' was already defined");
                return;
            }
            built.put(id, definition);
        } catch (IOException | RuntimeException notReadable) {
            problems.add(named + ": " + notReadable.getMessage());
        }
    }
}
