package dev.gathering.server;

import dev.gathering.network.Sending;
import dev.gathering.core.booster.BoosterConfig;
import dev.gathering.core.booster.BoosterOpener;
import dev.gathering.core.booster.MtgjsonCollation;
import dev.gathering.core.booster.OpenedPack;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.CardSummary;
import dev.gathering.service.CardDataService;
import dev.gathering.service.CollationService;
import dev.gathering.service.ServerSettings;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opening a booster: collation in, cards out, nothing about any set written down.
 *
 * <p>Three things happen off the server thread and one on it. The set's collation is fetched
 * and read, the pack is drawn from it, and the printings it named are resolved to card data -
 * all on the two pipelines that own those blocking calls. Only handing the cards over touches
 * a player, and that happens back on the server thread like every other inventory in the mod.
 *
 * <p>Every pack gets a fresh seed. A seed that came from anything a player could see or
 * influence - a position, a tick, a name - is a seed somebody works out, and a booster whose
 * contents can be predicted before it is opened is not a booster.
 *
 * <p>Behind {@code collection_enabled}, because a pack is cards being property and on a
 * server where cards are not property there is nothing to open.
 */
public final class PackOpening {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private static final SecureRandom SEEDS = new SecureRandom();

    /**
     * Which pack to open when nobody said, after whatever the server configured.
     *
     * <p>In the order a person means "a booster of this set": the modern retail pack, then
     * the draft pack sets used to have, then the set pack that replaced it for a while. Not a
     * rule about any set - every one of these is a name in somebody else's data, and a set
     * with none of them falls through to whatever it does publish.
     */
    private static final List<String> USUAL_KINDS = List.of("play", "draft", "set");


    private PackOpening() {
    }

    /**
     * Opens one pack of a set and gives the cards to the player.
     *
     * @param kind which product of that set, or blank for whatever this server calls a booster
     */
    public static void openFor(ServerPlayer player, String setCode, String kind) {
        openFor(player, setCode, kind, () -> { }, false);
    }

    /**
     * The same, for a caller that took something off a player to do it.
     *
     * @param giveBack run on the server thread if no pack comes out, however far along it
     *                 got. A booster that vanished from somebody's hand because a set turned
     *                 out to have no packs is worse than one that would not open.
     * @param ceremony whether to tell the client what came out so it can be opened by hand.
     *                 The cards are already given either way; this only decides whether there
     *                 is a wrapper to tear.
     */
    public static void openFor(
            ServerPlayer player, String setCode, String kind, Runnable giveBack,
            boolean ceremony) {
        String refusal = whyNot();
        if (refusal != null) {
            player.sendSystemMessage(Component.translatable(refusal));
            giveBack.run();
            return;
        }
        CollationService collation = CollationService.active().orElse(null);
        CardDataService cards = CardDataService.active().orElse(null);
        if (collation == null || cards == null) {
            player.sendSystemMessage(Component.translatable("message.gathering.pipeline_unavailable"));
            giveBack.run();
            return;
        }

        String set = setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT);
        collation.collationFor(set)
                // Async on the collation worker, not chained plainly: a set already read is
                // an already-completed future, and a plain chain would draw the pack on
                // whichever thread asked - which here is the server thread.
                .thenComposeAsync(reading -> {
                    BoosterConfig config = pick(reading, kind);
                    if (config == null) {
                        return java.util.concurrent.CompletableFuture.completedFuture(
                                new Opened(reading, null, null, List.of()));
                    }
                    OpenedPack pack = BoosterOpener.open(config, freshSeed(), config.id());
                    List<UUID> printings = new ArrayList<>();
                    for (CardIdentity card : pack.cards()) {
                        card.printing().ifPresent(printings::add);
                    }
                    return cards.findAll(printings)
                            .thenApply(found -> new Opened(reading, config, pack, found));
                }, collation.worker())
                .whenComplete((opened, failure) -> player.server.execute(() -> {
                    if (player.hasDisconnected()) {
                        // Nobody to hand cards to and nobody to hand a pack back to. The
                        // stack went with them; nothing here can help that.
                        return;
                    }
                    if (failure != null) {
                        LOGGER.warn("Opening a {} pack failed", set, failure);
                        player.sendSystemMessage(Component.translatable(
                                "message.gathering.pack_failed", Failures.rootMessage(failure)));
                        giveBack.run();
                        return;
                    }
                    if (opened.pack() == null) {
                        player.sendSystemMessage(nothingToOpen(opened.reading(), set, kind));
                        giveBack.run();
                        return;
                    }
                    deliver(player, opened, ceremony);
                }));
    }

    /**
     * Whether a printing is one a pack could ever have contained.
     *
     * <p>A set's oversized cards and its digital-only printings are in the set and were never
     * on a print sheet, so neither a fallback pack nor an audit of one should count them.
     *
     * <p>Nor are basic lands, which the rest of the mod already treats as free: a deck may
     * take as many as it likes without owning any. Counting them would put a basic in every
     * fallback pack, and would report a set as incomplete over five cards nobody has ever
     * needed to open a pack to get.
     */
    public static boolean wasEverInABooster(CardMetadata card) {
        return card != null && card.scryfallId() != null
                && !card.digitalOnly() && !card.oversized() && !card.isBasicLand();
    }

    /**
     * Why no pack can be opened on this server, as a translation key, or null if one can.
     *
     * <p>Its own method so the thing that will ask before showing somebody a pack - an item's
     * tooltip, a shop's shelf - asks the same question the opening does, rather than a second
     * copy of it that drifts.
     */
    public static String whyNot() {
        if (!ServerSettings.get().modes().collectionEnabled()) {
            return "message.gathering.pack_collection_off";
        }
        return null;
    }

    /**
     * A pack and the card data for what came out of it, or no pack and the reading that had
     * none to give - which is the difference between two very different sentences.
     */
    private record Opened(MtgjsonCollation.Reading reading, BoosterConfig config,
            OpenedPack pack, List<CardMetadata> cards) {
    }

    /**
     * Why nothing came out, in a sentence that says which of the two things went wrong.
     *
     * <p>"That set publishes no booster" and "that set publishes no booster of that name" are
     * not the same problem, and telling somebody the first when they typed the second sends
     * them off to check the set code that was right all along. The second lists what the set
     * does publish, because that is the next thing they would have to go and find out.
     */
    private static Component nothingToOpen(
            MtgjsonCollation.Reading reading, String set, String kind) {
        String key = whyNothingOpened(reading, kind);
        if (key.endsWith("no_collation")) {
            return Component.translatable(key, set);
        }
        return Component.translatable(key, set, kind.trim().toLowerCase(Locale.ROOT),
                String.join(", ", reading.packs().keySet()));
    }

    /** Which of the two sentences applies. Its own method so a test can ask. */
    public static String whyNothingOpened(MtgjsonCollation.Reading reading, String kind) {
        if (reading == null || reading.isEmpty() || kind == null || kind.isBlank()) {
            return "message.gathering.pack_no_collation";
        }
        return "message.gathering.pack_no_such_kind";
    }

    /**
     * The kind of pack to open.
     *
     * <p>What was asked for, then what the server calls a booster, then the usual retail
     * names, then whatever this set does publish - so a set that only ever had one kind of
     * pack opens rather than reporting that it has no "play" booster.
     */
    public static BoosterConfig pick(MtgjsonCollation.Reading reading, String kind) {
        if (reading == null || reading.isEmpty()) {
            return null;
        }
        if (kind != null && !kind.isBlank()) {
            return reading.pack(kind);
        }
        BoosterConfig configured = reading.pack(ServerSettings.get().collecting().boosterModel());
        if (configured != null) {
            return configured;
        }
        for (String usual : USUAL_KINDS) {
            BoosterConfig found = reading.pack(usual);
            if (found != null) {
                return found;
            }
        }
        return reading.packs().values().iterator().next();
    }

    /** What of a pack can actually be handed over, and how much of it could not. */
    public record Delivery(List<CardIdentity> giving, int unnameable) {

        public Delivery {
            giving = giving == null ? List.of() : List.copyOf(giving);
        }
    }

    /**
     * Pairs what the pack drew with what the card pipeline could name.
     *
     * <p>A printing the pipeline could not name is left out rather than handed over: the card
     * is real and the pack is right about it, but an item nothing can draw is worse on screen
     * than a pack one card short, and the count says so out loud.
     *
     * <p>Separate from the giving because this is the part with a decision in it. Putting a
     * stack in an inventory is vanilla's job and needs no test of ours.
     */
    public static Delivery whatToGive(OpenedPack pack, List<CardMetadata> named) {
        Map<UUID, CardMetadata> byPrinting = new LinkedHashMap<>();
        if (named != null) {
            for (CardMetadata card : named) {
                if (card != null && card.scryfallId() != null) {
                    byPrinting.put(card.scryfallId(), card);
                }
            }
        }
        List<CardIdentity> giving = new ArrayList<>();
        int unnameable = 0;
        for (CardIdentity card : pack.cards()) {
            UUID printing = card.printing().orElse(null);
            if (printing == null || !byPrinting.containsKey(printing)) {
                unnameable++;
                continue;
            }
            giving.add(card);
        }
        return new Delivery(giving, unnameable);
    }

    /**
     * The best card in what is being handed over, which is the one the pack was opened for.
     *
     * <p>The same rule the ceremony's own glow and reveal order use - see
     * {@link dev.gathering.core.ui.PackGlow} - so the card that came out last, ringed in its
     * rarity's colour, is the card that remembers coming out.
     */
    private static CardIdentity bestOf(List<CardIdentity> giving, List<CardMetadata> about) {
        CardIdentity best = null;
        int rank = Integer.MAX_VALUE;
        for (CardIdentity card : giving) {
            for (CardMetadata detail : about) {
                if (!card.printing().filter(detail.scryfallId()::equals).isPresent()) {
                    continue;
                }
                int at = dev.gathering.core.ui.PackGlow.rankOf(detail.rarity());
                if (at < rank) {
                    rank = at;
                    best = card;
                }
                break;
            }
        }
        return best;
    }

    /** Server thread only. */
    private static void deliver(ServerPlayer player, Opened opened, boolean ceremony) {
        // What was actually opened, not what was asked for. Asking for a set and no kind is
        // the ordinary way to open one from a console, and telling the screen the empty
        // string it was given draws a play booster in the draft booster's black.
        String set = opened.config().setCode();
        String kind = opened.config().kind();
        // Everything the client is about to hold, in one go rather than a packet a card:
        // a client told about a card before it holds one never renders a blank.
        List<CardSummary> summaries = new ArrayList<>();
        for (CardMetadata card : opened.cards()) {
            summaries.add(CardSummary.of(card));
        }
        for (CardMetadataPayload packet : CardMetadataPayload.inPackets(summaries)) {
            Sending.to(player, packet);
        }

        Delivery delivery = whatToGive(opened.pack(), opened.cards());
        // The one the pack was opened for remembers being opened. Only that one: a story on
        // every common out of every booster would be a story on every card in the game, which
        // is the same as no card having one. See CardStories.
        CardIdentity best = bestOf(delivery.giving(), opened.cards());
        for (CardIdentity card : delivery.giving()) {
            ItemStack stack = CardItem.of(CardComponent.of(card));
            if (card.equals(best)) {
                CardStories.remember(stack, CardStories.pulledBy(player, set));
            }
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        if (ceremony) {
            // After the cards, not before. The wrapper is theatre over something that has
            // already happened, so nothing about tearing it can go wrong enough to cost
            // somebody a booster.
            List<dev.gathering.item.CardComponent> shown = new ArrayList<>();
            for (CardIdentity card : delivery.giving()) {
                shown.add(dev.gathering.item.CardComponent.of(card));
            }
            Sending.to(player,
                    new dev.gathering.network.PackOpenedPayload(set, kind, shown));
        } else {
            // Quick opened, so there is no screen to say it on.
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.pack_opened", set.toUpperCase(Locale.ROOT), kind,
                    delivery.giving().size()));
        }
        if (delivery.unnameable() == 1) {
            player.sendSystemMessage(
                    Component.translatable("message.gathering.pack_unresolved_one"));
        } else if (delivery.unnameable() > 1) {
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.pack_unresolved", delivery.unnameable()));
        }
    }

    /**
     * A seed nobody can see coming.
     *
     * <p>Fresh per pack out of {@link SecureRandom}. The opener is deterministic on purpose -
     * an economy nobody can check what a pack should have held is an economy nobody can audit
     * - and that determinism is only safe as long as what goes into it is not guessable.
     */
    private static byte[] freshSeed() {
        byte[] seed = new byte[32];
        SEEDS.nextBytes(seed);
        return seed;
    }

}
