package dev.gathering.server;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.platform.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cards and packs this server owes somebody who was not there to take them.
 * <p>Opening a booster takes the pack out of your hand first, because a pack still in the hand
 * when the cards come back is a pack that can be opened twice. The cards then come from a
 * collation file and a metadata lookup, which is a round trip - and a player who logged out
 * during that round trip used to get nothing at all. The pack was gone, the cards were
 * dropped on the floor of a method that had nobody to hand them to, and the log said the
 * stack had gone with the player, which it had not: it had been consumed a moment earlier.
 * <p>So an opening that finds nobody to give to writes down what it owes, and the next time
 * that player joins they are handed it. The same file catches the other two ways a booster
 * can come up short: an opening that failed after the pack was consumed owes a pack, and a
 * card the pipeline could not name yet is owed rather than quietly dropped from the pack.
 * <p>One small file per player under the mod's data directory, like a wants list, and written
 * before this call returns rather than queued: what it is holding is somebody's property, and
 * the moment it is written down is the moment the pack has already gone.
 * <p>Server thread only.
 */
public final class Owed {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private static final String FOLDER = "owed";

    private static final String SUFFIX = ".txt";

    private static final String HEADING =
            "# What this server owes this player: one thing to a line, handed over when they join.";

    /**
     * A ceiling on one player's list, so a server that has gone wrong somewhere cannot write
     * a file without end. Far past any honest number of interrupted openings.
     */
    private static final int MOST_OWED = 2048;

    private Owed() {
    }

    /** Writes down that this player is owed a pack they paid for and never got. */
    public static void aPack(UUID player, String setCode, String kind) {
        if (player == null || setCode == null || setCode.isBlank()) {
            return;
        }
        add(player, List.of("pack " + setCode.trim().toLowerCase(Locale.ROOT)
                + " " + (kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT))));
    }

    /** Writes down cards that were rolled for somebody who was not there to take them. */
    public static void cards(UUID player, List<CardIdentity> cards) {
        if (player == null || cards == null || cards.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>(cards.size());
        for (CardIdentity card : cards) {
            if (card == null) {
                continue;
            }
            String printing = card.printing().map(UUID::toString).orElse(null);
            if (printing != null) {
                lines.add("card " + printing + " " + card.foil());
            } else {
                card.custom().ifPresent(id -> lines.add("custom " + id + " " + card.foil()));
            }
        }
        add(player, lines);
    }

    /**
     * Hands over everything owed, if anything is.
     * <p>Called when a player joins. The file goes only once its contents are in the player's
     * hands: if handing over throws, the list is still on disk and they get it next time.
     */
    public static void deliver(ServerPlayer player) {
        if (player == null) {
            return;
        }
        List<String> lines = read(player.getUUID());
        if (lines.isEmpty()) {
            return;
        }
        int handed = 0;
        for (String line : lines) {
            ItemStack stack = itemFor(line);
            if (stack != null && !stack.isEmpty()) {
                Handing.give(player, stack);
                handed++;
            }
        }
        forget(player.getUUID());
        if (handed > 0) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.gathering.owed_delivered", handed));
        }
    }

    /** What a line means, or null for one this version cannot read. */
    private static ItemStack itemFor(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        try {
            return switch (parts[0]) {
                case "pack" -> PackItem.of(new PackComponent(
                        parts[1], parts.length > 2 ? parts[2] : ""));
                case "card" -> CardItem.of(new CardComponent(
                        java.util.Optional.of(UUID.fromString(parts[1])),
                        parts.length > 2 && Boolean.parseBoolean(parts[2]),
                        java.util.Optional.empty(), false));
                case "custom" -> CardItem.of(new CardComponent(
                        java.util.Optional.empty(),
                        parts.length > 2 && Boolean.parseBoolean(parts[2]),
                        java.util.Optional.of(parts[1]), false));
                default -> null;
            };
        } catch (IllegalArgumentException unreadable) {
            LOGGER.warn("Could not read an owed line: {}", line);
            return null;
        }
    }

    private static void add(UUID player, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        List<String> all = new ArrayList<>(read(player));
        all.addAll(lines);
        if (all.size() > MOST_OWED) {
            all = all.subList(all.size() - MOST_OWED, all.size());
        }
        write(player, all);
    }

    private static List<String> read(UUID player) {
        Path where = fileFor(player);
        if (where == null || !Files.isRegularFile(where)) {
            return List.of();
        }
        try {
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(where, StandardCharsets.UTF_8)) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (IOException couldNotRead) {
            LOGGER.warn("Could not read what is owed at {}: {}", where, couldNotRead.getMessage());
            return List.of();
        }
    }

    private static void write(UUID player, List<String> lines) {
        Path where = fileFor(player);
        if (where == null) {
            return;
        }
        try {
            Files.createDirectories(where.getParent());
            List<String> out = new ArrayList<>(lines.size() + 1);
            out.add(HEADING);
            out.addAll(lines);
            Files.write(where, out, StandardCharsets.UTF_8);
        } catch (IOException couldNotWrite) {
            LOGGER.error("Could not write down what is owed to {}: {}", player, couldNotWrite.getMessage());
        }
    }

    /** Forgets a delivered list. Public so a test can start from nothing. */
    public static void forget(UUID player) {
        Path where = fileFor(player);
        if (where == null) {
            return;
        }
        try {
            Files.deleteIfExists(where);
        } catch (IOException couldNotDelete) {
            LOGGER.warn("Could not clear what was owed at {}: {}", where, couldNotDelete.getMessage());
        }
    }

    /** Whether anything is waiting for this player, which is what a test asks. */
    public static int waitingFor(UUID player) {
        return read(player).size();
    }

    private static Path fileFor(UUID player) {
        try {
            return Platform.get().dataDirectory().resolve(FOLDER).resolve(player + SUFFIX);
        } catch (RuntimeException noPlatform) {
            return null;
        }
    }
}
