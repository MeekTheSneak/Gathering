package dev.gathering.server;

import dev.gathering.core.card.SetCode;
import dev.gathering.core.card.SetRelease;
import dev.gathering.service.CardDataService;
import dev.gathering.service.ServerSettings;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    private static volatile CompletableFuture<Optional<String>> resolving =
            CompletableFuture.completedFuture(Optional.empty());

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
            resolving = CompletableFuture.completedFuture(Optional.empty());
            return;
        }
        String configured = settings.collecting().currentSet();
        if (!configured.equals(AUTO)) {
            Optional<String> named = SetCode.of(configured);
            if (named.isEmpty()) {
                LOGGER.warn("collection.current_set is \"{}\", which is not a set code and not "
                        + "\"auto\", so this server has no current set.", configured);
            }
            resolving = CompletableFuture.completedFuture(named);
            return;
        }

        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            resolving = CompletableFuture.completedFuture(Optional.empty());
            return;
        }
        resolving = cards.currentSet(today()).handle((newest, failure) -> {
            if (failure != null) {
                LOGGER.warn("Could not ask Scryfall which set is current, so this server has "
                        + "none. Name one in collection.current_set to run without asking.",
                        failure);
                return Optional.<String>empty();
            }
            Optional<String> code = newest.map(SetRelease::code).flatMap(SetCode::of);
            code.ifPresentOrElse(
                    found -> LOGGER.info("The current set is {} ({}), from Scryfall's own list",
                            found.toUpperCase(java.util.Locale.ROOT),
                            newest.map(SetRelease::name).orElse(found)),
                    () -> LOGGER.warn("Scryfall's list of sets held nothing released yet, so "
                            + "this server has no current set."));
            return code;
        });
    }

    /**
     * The current set, once it is known.
     *
     * <p>Empty where a server has no current set at all - it named something that is not a
     * set code, or the list could not be fetched. Everything downstream treats that the same
     * way it treats a set with no products: nothing to sell and nothing to drop.
     */
    public static CompletableFuture<Optional<String>> whenKnown() {
        return resolving;
    }

    /** Between servers, so one world's answer is not the next one's. */
    public static void clear() {
        resolving = CompletableFuture.completedFuture(Optional.empty());
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
