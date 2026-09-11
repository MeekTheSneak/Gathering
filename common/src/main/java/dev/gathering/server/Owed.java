package dev.gathering.server;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
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
 * <p>One small file per player <em>inside the save</em>, and written before this call returns
 * rather than queued: what it is holding is somebody's property, and the moment it is written
 * down is the moment the pack has already gone.
 * <p>Inside the save rather than under the game directory, which is where it started. Two
 * single-player worlds in one installation share a game directory, so they shared this file:
 * a booster interrupted in one world could be claimed on joining the other, and was then gone
 * from the world that owed it. Card metadata is downloaded once and shared on purpose;
 * property is not. See {@link ServerRun#saveDirectory()}.
 * <p>Nothing on this list is ever dropped to make room. A cap that throws away the oldest
 * entries is a cap that deletes cards somebody already earned, which is worse than the runaway
 * file it was guarding against - so the cap now refuses to <em>accept</em> beyond the ceiling
 * and says so, and what is already written stays written.
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
     * <p>Enforced by refusing new entries, never by discarding old ones. It used to keep the
     * newest {@value #MOST_OWED} and drop the rest, which meant the safety net itself deleted
     * property: an audit recorded 2,049 owed cards and found 2,048 waiting. A list this long
     * is a fault somewhere else, and the answer to a fault is to stop and shout, not to start
     * quietly throwing away cards.
     */
    private static final int MOST_OWED = 2048;

    private Owed() {
    }

    /**
     * Writes down that a pack is <em>about</em> to be opened, before it leaves the hand.
     * <p>The receipt, and the reason the rest of this file is not enough on its own. A pack is
     * consumed first - it has to be, or one still in the hand when the cards come back is a
     * pack that can be opened twice - and everything after that is a round trip through a
     * collation file and a metadata lookup. The debt used to be written from the far end of
     * that trip, which covers a player logging out and covers nothing else: a server stopped
     * in the middle cancels the queued work, the completion never runs, and there is no
     * record anywhere that a booster ever existed.
     * <p>So the record is written first and settled at the end. If this server never reaches
     * the end, the receipt is still on disk and the next time that player joins they are
     * handed the pack back - exactly one pack, because settling replaces the receipt rather
     * than adding to it.
     *
     * @return the receipt to settle, or empty if nothing could be written - in which case the
     *     caller must not consume the pack
     */
    public static java.util.Optional<String> opening(
            UUID player, String setCode, String kind, String color) {
        if (player == null || setCode == null || setCode.isBlank()) {
            return java.util.Optional.empty();
        }
        String receipt = UUID.randomUUID().toString();
        // The color is part of what the pack is, not decoration on it. A starter booster is
        // a white one or a red one because somebody chose, and a receipt that recorded only
        // the set and the kind handed back a pack that could open as anything - which is the
        // choice quietly undone by a crash. Written last so a line from before this field
        // existed still reads: a receipt with three words is the same receipt with no color.
        boolean written = add(player, List.of("opening " + receipt + " "
                + setCode.trim().toLowerCase(Locale.ROOT)
                + " " + wordFor(kind)
                + " " + wordFor(color)));
        return written ? java.util.Optional.of(receipt) : java.util.Optional.empty();
    }

    /**
     * One field of a receipt line, safe to write and safe to leave out.
     * <p>A blank becomes a dash rather than nothing, because the fields are separated by
     * spaces and an empty one in the middle would shift every field after it along. Read back
     * by {@link #wordBack}.
     */
    private static String wordFor(String value) {
        String kept = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return kept.isEmpty() ? "-" : kept;
    }

    /** The other half of {@link #wordFor}: a dash, or a missing field, is nothing. */
    private static String wordBack(String[] parts, int at) {
        if (parts.length <= at) {
            return "";
        }
        String word = parts[at];
        return "-".equals(word) ? "" : word;
    }

    /**
     * The opening is over: the receipt goes, and whatever is still owed takes its place.
     * <p>One write, so there is no moment where both the receipt and the cards are on the
     * list. Pass an empty list when the player has the cards in their hands already.
     * <p>A receipt that is not on the list has already been settled, by this server or by a
     * join that handed the pack back. Adding the cards then would be handing over twice, so
     * it does nothing and says it worked.
     *
     * @return whether the list is now correct on disk
     */
    public static boolean settled(UUID player, String receipt, List<CardIdentity> cards) {
        if (player == null || receipt == null || receipt.isBlank()) {
            return false;
        }
        List<String> lines = new ArrayList<>(read(player));
        boolean found = lines.removeIf(line -> line.startsWith("opening " + receipt + " ")
                || line.equals("opening " + receipt));
        if (!found) {
            return true;
        }
        lines.addAll(linesFor(cards));
        return write(player, lines);
    }

    /**
     * Writes down that this player is owed a pack they paid for and never got.
     *
     * @return whether it is safely on disk. A caller told false must not report the pack as
     *     safeguarded: nothing was recorded and nobody will hand it over.
     */
    public static boolean aPack(UUID player, String setCode, String kind, String color) {
        if (player == null || setCode == null || setCode.isBlank()) {
            return false;
        }
        return add(player, List.of("pack " + setCode.trim().toLowerCase(Locale.ROOT)
                + " " + wordFor(kind)
                + " " + wordFor(color)));
    }

    /**
     * Writes down cards that were rolled for somebody who was not there to take them.
     *
     * @return whether they are safely on disk, with the same warning as {@link #aPack}
     */
    public static boolean cards(UUID player, List<CardIdentity> cards) {
        if (player == null || cards == null || cards.isEmpty()) {
            return false;
        }
        return add(player, linesFor(cards));
    }

    /** Cards as lines of this file, skipping anything that names no printing at all. */
    private static List<String> linesFor(List<CardIdentity> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
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
        return lines;
    }

    /**
     * Hands over everything owed, if anything is.
     * <p><b>The list is shortened first, and only then are the items handed over.</b> That
     * order is the whole of this method and it is the opposite of what it used to be.
     * <p>Handing over first and clearing after reads as the safe way round - if the giving
     * fails, the debt is still written down - and it is not, because clearing can fail too.
     * An audit blocked the ledger's temporary-write path and called delivery twice: one debt
     * came out as two packs, because the give succeeded, the rewrite silently did not, and the
     * next join read the same line again. Between losing a card and printing one, printing one
     * is worse: a lost card is a complaint somebody can answer, and a printed one is an
     * economy nobody can trust.
     * <p>So a line that cannot be struck off is a line that is not paid out. If the write
     * fails nothing is handed over at all and the whole list waits for the next join, which is
     * the same debt it was.
     * <p>A line this version cannot make an item out of stays on the list rather than being
     * swept up with the delivered ones. An older save read by a newer mod, or the reverse, is
     * not allowed to be a way to lose a card.
     */
    public static void deliver(ServerPlayer player) {
        if (player == null) {
            return;
        }
        List<String> lines = read(player.getUUID());
        if (lines.isEmpty()) {
            return;
        }

        // What can be handed over, and what this version cannot read and must keep.
        List<ItemStack> giving = new ArrayList<>();
        List<String> keeping = new ArrayList<>();
        for (String line : lines) {
            ItemStack stack = itemFor(line);
            if (stack != null && !stack.isEmpty()) {
                giving.add(stack);
            } else {
                keeping.add(line);
            }
        }
        if (giving.isEmpty()) {
            if (!keeping.isEmpty()) {
                LOGGER.warn("Keeping {} owed line(s) this version cannot read, for {}",
                        keeping.size(), player.getUUID());
            }
            return;
        }

        // Struck off first. A write that fails hands over nothing: the debt is still exactly
        // what it was, and the next join tries again.
        boolean struckOff = keeping.isEmpty()
                ? forget(player.getUUID())
                : write(player.getUUID(), keeping);
        if (!struckOff) {
            LOGGER.error("Could not shorten what is owed to {}, so nothing was handed over."
                    + " They keep the debt and it will be tried again next time they join.",
                    player.getUUID());
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.gathering.owed_could_not_be_paid"));
            return;
        }

        for (ItemStack stack : giving) {
            Handing.give(player, stack);
        }
        if (!keeping.isEmpty()) {
            LOGGER.warn("Kept {} owed line(s) this version cannot read, for {}",
                    keeping.size(), player.getUUID());
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.gathering.owed_delivered", giving.size()));
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
                        parts[1], wordBack(parts, 2), wordBack(parts, 3)));
                // A receipt nobody settled: this server, or a previous one, took the pack and
                // never finished opening it. The pack comes back, once, as the pack it was -
                // set, kind and color. A line written before the color existed has three
                // words and reads back as no color, which is what it meant.
                case "opening" -> parts.length < 3 ? null : PackItem.of(new PackComponent(
                        parts[2], wordBack(parts, 3), wordBack(parts, 4)));
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

    /**
     * Adds to a player's list, and says whether it is safely written down.
     *
     * @return false if nothing could be recorded, so the caller must not treat the property
     *     as safeguarded and must keep it some other way
     */
    private static boolean add(UUID player, List<String> lines) {
        if (lines.isEmpty()) {
            return true;
        }
        List<String> all = new ArrayList<>(read(player));
        all.addAll(lines);
        if (all.size() > MOST_OWED) {
            // Over the ceiling, so something is wrong - but the entries already on the list
            // are somebody's cards and are not the thing to sacrifice. Write all of it and
            // shout, rather than trimming the oldest and reporting success.
            LOGGER.error("The owed list for {} has reached {} entries, past the {} this expects."
                    + " Nothing has been dropped; look at why openings are not completing.",
                    player, all.size(), MOST_OWED);
        }
        return write(player, all);
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

    /**
     * Writes a player's whole list, all at once or not at all.
     * <p>Through a neighboring temporary file and a move, because the alternative is a
     * truncate-then-write: a crash between the two leaves an empty list where somebody's
     * cards were. The move is atomic where the filesystem offers it and a plain replace where
     * it does not, which is still strictly better than writing in place.
     *
     * @return whether the list is now on disk
     */
    private static boolean write(UUID player, List<String> lines) {
        Path where = fileFor(player);
        if (where == null) {
            LOGGER.error("There is nowhere to write down what is owed to {}: no server is running",
                    player);
            return false;
        }
        Path partly = where.resolveSibling(where.getFileName() + ".writing");
        try {
            Files.createDirectories(where.getParent());
            List<String> out = new ArrayList<>(lines.size() + 1);
            out.add(HEADING);
            out.addAll(lines);
            Files.write(partly, out, StandardCharsets.UTF_8);
            try {
                Files.move(partly, where, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException notHere) {
                Files.move(partly, where, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException couldNotWrite) {
            LOGGER.error("Could not write down what is owed to {}: {}", player, couldNotWrite.getMessage());
            try {
                Files.deleteIfExists(partly);
            } catch (IOException leaveIt) {
                LOGGER.warn("And could not tidy up {}: {}", partly, leaveIt.getMessage());
            }
            return false;
        }
    }

    /**
     * Forgets a delivered list, and says whether it really is gone.
     * <p>The answer matters: {@link #deliver} strikes a debt off before paying it, so a delete
     * that quietly failed and reported success would be a debt paid twice.
     *
     * @return whether there is now no list on disk for this player
     */
    public static boolean forget(UUID player) {
        Path where = fileFor(player);
        if (where == null) {
            // Nowhere to write means nowhere to read either, so there is no debt to strike.
            return true;
        }
        try {
            Files.deleteIfExists(where);
            return !Files.exists(where);
        } catch (IOException couldNotDelete) {
            LOGGER.warn("Could not clear what was owed at {}: {}", where, couldNotDelete.getMessage());
            return false;
        }
    }

    /** Whether anything is waiting for this player, which is what a test asks. */
    public static int waitingFor(UUID player) {
        return read(player).size();
    }

    /**
     * Where this save keeps what it owes one player, or null if no save is open.
     * <p>Null rather than a game-directory fallback on purpose: a fallback is exactly the
     * sharing between worlds this moved away from, and silently writing somebody's cards
     * into the wrong world is worse than refusing and saying so.
     */
    private static Path fileFor(UUID player) {
        return ServerRun.inSave(FOLDER).map(folder -> folder.resolve(player + SUFFIX)).orElse(null);
    }
}
