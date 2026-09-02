package dev.gathering.server;

import dev.gathering.core.card.SetCode;
import dev.gathering.core.card.SetRelease;
import dev.gathering.core.config.GatheringConfig;
import dev.gathering.service.CardDataService;
import dev.gathering.service.ServerSettings;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Which set this server is selling and dropping.
 *
 * <p>The config names a set code, or says {@code "auto"} and means the one on the shelves.
 * That second answer is the default, and it is the one worth getting right: a server
 * installed two years from now should be current on the day it starts, with nobody editing
 * anything. Scryfall publishes the list of every set, so this asks it.
 *
 * <p>Asked once, when the server starts, off every game thread. Sets come out every few
 * months and a restart is a much shorter interval than that, so nothing here re-checks;
 * what it costs is one request per server start, and what it buys is that the setting
 * nobody touched is the setting that works.
 *
 * <p>A server that named its own set never asks at all, which is also how an era server -
 * one deliberately living in a chosen block of Magic history - stays where it was put.
 */
public final class CurrentSet {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    /** What the config file writes when it wants the newest release rather than a set. */
    public static final String AUTO = "auto";

    /**
     * How many releases back {@code "recent"} could ever reach.
     *
     * <p>What the one fetch asks for. The config clamps its own number to this, so raising
     * the setting never means asking Scryfall again.
     *
     * <p>{@code loot_sets = ["all"]} is not clamped by it - see {@link #howManyReleases} -
     * because a server that asked for every set and quietly got the newest sixty-four is a
     * server whose config said one thing and did another.
     */
    private static final int MOST_RECENT_SETS = 64;

    /**
     * The whole answer: what the config pinned, and what Scryfall said is out.
     *
     * <p>One object rather than two fields. They are two halves of "which set is current" and
     * a reader that caught the new list beside the old pin would get an answer neither of
     * them gave.
     */
    private record Answer(Optional<String> pinned, CompletableFuture<List<SetRelease>> releases) {

        static final Answer NOTHING =
                new Answer(Optional.empty(), CompletableFuture.completedFuture(List.of()));

        static Answer pinnedTo(Optional<String> code) {
            return new Answer(code, CompletableFuture.completedFuture(List.of()));
        }
    }

    private static volatile Answer answer = Answer.NOTHING;

    private CurrentSet() {
    }

    /**
     * Works out which set this server means, once.
     *
     * <p>Every later caller waits on the same answer rather than asking again.
     */
    public static void resolve() {
        var settings = ServerSettings.get();
        if (!settings.modes().collectionEnabled()) {
            // Nothing on this server cares which set is current, and asking anyway would be
            // a request every play-only server makes at every start for an answer nobody
            // reads.
            answer = Answer.NOTHING;
            return;
        }
        String configured = settings.collecting().currentSet();
        Optional<String> pinned =
                configured.equals(AUTO) ? Optional.empty() : SetCode.of(configured);
        if (!configured.equals(AUTO) && pinned.isEmpty()) {
            LOGGER.warn("collection.current_set is \"{}\", which is not a set code and not "
                    + "\"auto\", so this server has no current set.", configured);
        }
        // "current" alone is answered by a pinned set without asking anybody; "recent" is
        // the only thing that actually needs the list of releases.
        boolean wantsTheList = dev.gathering.core.sealed.LootSets
                .needsMoreThanTheNewest(settings.collecting().lootSets());
        if (!configured.equals(AUTO) && !wantsTheList) {
            // Nothing on this server needs Scryfall's list: it named its current set, and its
            // loot draws from sets it named too.
            answer = Answer.pinnedTo(pinned);
            return;
        }

        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            // No pipeline to ask with. A set the config named is still a set, so the pin is
            // kept rather than thrown away with the list that was never going to arrive.
            answer = Answer.pinnedTo(pinned);
            return;
        }
        answer = new Answer(pinned, cards.premierSets(
                today(), howManyReleases(settings.collecting().lootSets()))
                .handle((sets, failure) -> {
            if (failure != null) {
                LOGGER.warn("Could not ask Scryfall which sets are out, so this server has no "
                        + "current set. Name one in collection.current_set to run without "
                        + "asking.", failure);
                return List.<SetRelease>of();
            }
            if (sets.isEmpty()) {
                LOGGER.warn("Scryfall's list of sets held nothing released yet, so this server "
                        + "has no current set.");
            } else if (pinned.isEmpty()) {
                LOGGER.info("The current set is {} ({}), from Scryfall's own list",
                        sets.getFirst().code().toUpperCase(java.util.Locale.ROOT),
                        sets.getFirst().name());
            }
            return sets;
        }));
    }

    /**
     * The current set, once it is known.
     *
     * <p>Empty where a server has no current set at all - it named something that is not a
     * set code, or the list could not be fetched. Everything downstream treats that the same
     * way it treats a set with no products: nothing to sell and nothing to drop.
     */
    public static CompletableFuture<Optional<String>> whenKnown() {
        Answer now = answer;
        return now.releases().thenApply(sets -> now.pinned().isPresent()
                ? now.pinned()
                : sets.stream().findFirst().map(SetRelease::code).flatMap(SetCode::of));
    }

    /**
     * The last few releases, newest first, once they are known.
     *
     * <p>What a server drawing its packs from more than one set draws from. Out of the same
     * list the current set came from, so it costs nothing beyond the one request.
     */
    public static CompletableFuture<List<String>> recent(int howMany) {
        return answer.releases().thenApply(sets -> sets.stream()
                .limit(Math.max(0, howMany))
                .map(SetRelease::code)
                .flatMap(code -> SetCode.of(code).stream())
                .toList());
    }

    /**
     * How far back the one fetch reaches, which is as far as the config could ever ask.
     *
     * <p>A window for {@code "recent"} and no window at all for {@code "all"}. Every premier
     * set there has ever been is a few hundred entries out of a list already in hand, so the
     * difference costs nothing but says the truth.
     */
    private static int howManyReleases(java.util.List<String> lootSets) {
        return dev.gathering.core.sealed.LootSets.wantsEverySet(lootSets)
                ? Integer.MAX_VALUE
                : MOST_RECENT_SETS;
    }

    /** Between servers, so one world's answer is not the next one's. */
    public static void clear() {
        answer = Answer.NOTHING;
    }

    /**
     * Today, as Scryfall writes a release date.
     *
     * <p>UTC rather than the machine's zone: a release date is a date Wizards published, not
     * a moment, and a server in Auckland and one in Los Angeles should agree about which set
     * is current rather than disagreeing for most of a day around every release.
     */
    private static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }
}
