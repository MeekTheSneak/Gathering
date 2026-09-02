package dev.gathering.server;

import dev.gathering.core.deck.ResolvedDeck;
import dev.gathering.core.loaner.LoanerShelf;
import dev.gathering.item.DeckComponent;
import dev.gathering.platform.Platform;
import dev.gathering.service.CardDataService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decks the server lends out.
 * <p>The starter-deck moment. Somebody joins, owns nothing, and may be on a server where
 * importing is off entirely - and they can still walk to a table, sit down and play a real
 * deck in their first minute. Without this that player's first hour is asking somebody else
 * to make them a deck.
 * <p>Admin-defined and file-shaped on purpose: a decklist is a hundred lines of text and a
 * command is a single line, so the way to add one is to drop a list in a folder. The folder
 * is written on first start with one deck already in it, so a server that touches nothing is
 * a server that can already lend.
 * <p>Resolved once, when the server starts, through the same pipeline every other decklist
 * goes through - so a loaner costs no requests when somebody takes it, and a list naming a
 * card that does not exist is a line in the log at boot rather than a failure in front of a
 * new player. What resolution produces is kept as a {@link DeckComponent} and copied per
 * borrower; nobody is handed the shared one.
 */
public final class LoanerDecks {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** Where the lists live, next to the config file rather than inside it. */
    public static final String DIRECTORY = "gathering-loaners";

    /** What a decklist file is called. Anything else in the folder is left alone. */
    private static final List<String> ENDINGS = List.of(".txt", ".dec", ".mtga");

    /**
     * A loaner nobody has to write: a real deck out of cards every set has.
     * <p>Written once, if the folder is not there. A server that wants no loaners deletes it
     * and the folder stays empty, because this only ever runs when there is no folder at all.
     */
    private static final String SAMPLE = String.join("\n",
            "// A loaner deck. Drop more lists in this folder, one file each, and the file",
            "// name is what players see. Anything a decklist can say, these can say.",
            "//",
            "// Nobody needs to own these cards, and importing does not have to be on: a",
            "// loaner is the server lending a deck, which is why it works when neither does.",
            "//",
            "// Walking up to a table and sitting down starts Commander, so a loaner meant for",
            "// that wants a Commander section and ninety-nine cards. This one is a sixty-card",
            "// constructed deck, which plays fine and will be called odd at a Commander table.",
            "",
            "Deck",
            "4 Llanowar Elves",
            "4 Elvish Mystic",
            "4 Grizzly Bears",
            "4 Giant Growth",
            "4 Rampant Growth",
            "4 Garruk's Companion",
            "4 Leatherback Baloth",
            "3 Overrun",
            "3 Beast Within",
            "2 Vivien Reid",
            "24 Forest");

    /**
     * What is on the shelf, by name.
     * <p>Sorted rather than merely concurrent, because the order is the menu's order and it
     * has to be the same on every client and after every restart. {@link LoanerShelf} sorts
     * the files it reads by the same rule; keeping a second ordered list beside this map was
     * two statements of one rule, and the two disagreed the moment a deck arrived any way but
     * out of a file - which is exactly what a deck stocked by a test or a command is.
     */
    private static final java.util.concurrent.ConcurrentSkipListMap<String, DeckComponent> SHELF =
            new java.util.concurrent.ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);

    private LoanerDecks() {
    }

    /** The names a player may ask for, in the order they are offered in. */
    public static List<String> names() {
        return List.copyOf(SHELF.keySet());
    }

    /** Whether this server lends anything at all. */
    public static boolean lends() {
        return !SHELF.isEmpty();
    }

    /**
     * The deck by that name, made out for this borrower.
     * <p>A fresh component every time, owned by whoever asked. The shelf copy is never handed
     * out: a loaner that came back with somebody else's name on it would be a loaner nobody
     * else could put down.
     */
    public static Optional<DeckComponent> borrow(String name, UUID borrower) {
        DeckComponent deck = name == null ? null : SHELF.get(name);
        if (deck == null) {
            return Optional.empty();
        }
        return Optional.of(new DeckComponent(
                deck.name(), deck.description(), Optional.ofNullable(borrower),
                deck.entries(), deck.commanders(), deck.sideboard())
                // The shelf's own, not the borrower's: two people who took the same deck
                // should be able to see at a glance that they did.
                .colored(dev.gathering.core.card.DeckColors.pick(deck.name().hashCode())));
    }

    /**
     * Puts a deck on the shelf under that name.
     * <p>The one way onto it. Reading a folder produces a name and a resolved deck and calls
     * this; so would an admin command that lent a deck somebody had in their hands, and so
     * does the test that proves borrowing works without a network in the way.
     */
    public static void stock(String name, DeckComponent deck) {
        if (name == null || name.isBlank() || deck == null) {
            return;
        }
        SHELF.put(name, deck);
    }

    /** Between servers, so one world's shelf is not the next one's. */
    public static void clear() {
        SHELF.clear();
    }

    /**
     * Reads the folder and starts resolving what is in it.
     * <p>Returns immediately. Resolution is a Scryfall lookup per distinct card and runs on
     * the card pipeline, so a server with ten loaners is not a server that boots ten lookups
     * slower - it is one where the decks arrive on the shelf a moment after the world does.
     */
    public static void warm() {
        read();
    }

    /**
     * Reads the folder again, for an admin who has just added a decklist.
     * <p>Without this, lending a new deck means restarting the server, which for the one
     * feature whose whole point is a new player's first minute is a poor trade.
     * <p>Nothing is taken off the shelf until the replacement is ready. Clearing first and
     * resolving afterwards would leave a window - seconds wide, because resolving is a
     * network call - where the server lends nothing at all, and somebody sitting down inside
     * it is told this server has no decks.
     *
     * @return how many decklists the folder held, which is not yet how many are lent
     */
    public static java.util.concurrent.CompletableFuture<Integer> reload() {
        return read();
    }

    private static java.util.concurrent.CompletableFuture<Integer> read() {
        LoanerShelf shelf = LoanerShelf.of(readFolder(folder()));
        if (shelf.isEmpty()) {
            SHELF.clear();
            return java.util.concurrent.CompletableFuture.completedFuture(0);
        }
        CardDataService service = CardDataService.active().orElse(null);
        if (service == null) {
            LOGGER.warn("There are {} loaner deck(s) but no card pipeline to read them with",
                    shelf.size());
            return java.util.concurrent.CompletableFuture.completedFuture(0);
        }

        // Built beside the shelf and swapped in at the end, so a reload never empties it.
        Map<String, DeckComponent> staging = new java.util.concurrent.ConcurrentHashMap<>();
        List<java.util.concurrent.CompletableFuture<?>> resolving = new ArrayList<>();
        for (LoanerShelf.Loaner loaner : shelf.decks()) {
            resolving.add(resolve(service, loaner, staging));
        }
        return java.util.concurrent.CompletableFuture
                .allOf(resolving.toArray(java.util.concurrent.CompletableFuture[]::new))
                .thenApply(ignored -> {
                    SHELF.keySet().retainAll(staging.keySet());
                    SHELF.putAll(staging);
                    return staging.size();
                });
    }

    private static java.util.concurrent.CompletableFuture<?> resolve(
            CardDataService service, LoanerShelf.Loaner loaner,
            Map<String, DeckComponent> staging) {
        return service.importDecklist(loaner.decklist()).handle((deck, failure) -> {
            if (failure != null) {
                LOGGER.warn("The loaner deck \"{}\" could not be read: {}",
                        loaner.name(), failure.getMessage());
                return null;
            }
            if (deck == null || deck.cards().isEmpty()) {
                LOGGER.warn("The loaner deck \"{}\" resolved to no cards at all, so it is not"
                        + " being lent", loaner.name());
                return null;
            }
            // Named after the file rather than after whatever the list called itself. The
            // name on the button and the name the client sends back have to be the same
            // string, and the file is the one an admin can see.
            DeckComponent made = DecklistImport.toComponent(
                    deck, null, loaner.name(), description(deck));
            staging.put(loaner.name(), made);
            LOGGER.info("Lending \"{}\": {} cards{}", loaner.name(), made.deckSize(),
                    problems(deck));
            return null;
        });
    }

    private static String description(ResolvedDeck deck) {
        return deck.unresolved().isEmpty()
                ? "A deck the server lends out."
                : "A deck the server lends out. " + deck.unresolved().size()
                        + " line(s) could not be read.";
    }

    private static String problems(ResolvedDeck deck) {
        int count = deck.problems().size() + deck.unresolved().size();
        return count == 0 ? "" : ", with " + count + " line(s) that could not be read";
    }

    private static Path folder() {
        return Platform.get().configDirectory().resolve(DIRECTORY);
    }

    /**
     * Every decklist in the folder, by file name.
     * <p>A folder that is not there is written with one deck in it, so the feature is on
     * before anybody has read about it. A folder that cannot be read at all is a warning and
     * an empty shelf: a server always starts.
     */
    private static Map<String, String> readFolder(Path folder) {
        try {
            if (!Files.isDirectory(folder)) {
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("green_stompy.txt"), SAMPLE,
                        StandardCharsets.UTF_8);
                LOGGER.info("Wrote {} with a deck in it; drop more lists there to lend them",
                        folder);
            }
        } catch (IOException couldNotWrite) {
            LOGGER.warn("Could not set up the loaner deck folder: {}", couldNotWrite.getMessage());
            return Map.of();
        }

        Map<String, String> files = new LinkedHashMap<>();
        try (Stream<Path> listing = Files.list(folder)) {
            for (Path file : listing.sorted().toList()) {
                if (!Files.isRegularFile(file) || !isDecklist(file)) {
                    continue;
                }
                try {
                    files.put(file.getFileName().toString(),
                            Files.readString(file, StandardCharsets.UTF_8));
                } catch (IOException couldNotRead) {
                    LOGGER.warn("Could not read the loaner deck {}: {}",
                            file.getFileName(), couldNotRead.getMessage());
                }
            }
        } catch (IOException couldNotList) {
            LOGGER.warn("Could not read the loaner deck folder: {}", couldNotList.getMessage());
        }
        return files;
    }

    private static boolean isDecklist(Path file) {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return ENDINGS.stream().anyMatch(name::endsWith);
    }
}
