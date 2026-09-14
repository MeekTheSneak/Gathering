package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.client.ClientCardCache;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.card.UnresolvedCards;
import dev.gathering.network.CardSummary;
import dev.gathering.network.CardsUnresolvedPayload;
import dev.gathering.server.CardMetadataRequests;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A card lookup that ends without a name says why, and the client shows it.
 * <p>A printing Scryfall does not know and a printing that could not be looked up both used to
 * send nothing, so both said "Loading" for as long as anybody looked. These go through the
 * server's sorting and the client cache every screen asks, rather than the pure tracker alone.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CardLookupOutcomeGameTest {

    private static final UUID REAL = new UUID(71L, 1L);
    private static final UUID GHOST = new UUID(71L, 2L);

    /** A batch that found some and not others names the found ones and calls the rest missing. */
    @GameTest(template = "tables")
    public static void amixedbatchnamesthefoundandcallstherestmissing(GameTestHelper helper) {
        var answer = CardMetadataRequests.answerFor(List.of(REAL, GHOST), List.of(card(REAL)), null);
        if (answer.found().size() != 1 || !answer.found().get(0).scryfallId().equals(REAL)) {
            helper.fail("the found card was not answered: " + answer.found());
            return;
        }
        if (!answer.unresolved().missing().equals(List.of(GHOST))
                || !answer.unresolved().unavailable().isEmpty()) {
            helper.fail("the card not found was not called missing: " + answer.unresolved());
            return;
        }
        var everything = CardMetadataRequests.answerFor(List.of(REAL), List.of(card(REAL)), null);
        if (!everything.unresolved().isEmpty()) {
            helper.fail("a batch that found everything still said something was missing");
            return;
        }
        var nothing = CardMetadataRequests.answerFor(List.of(REAL, GHOST), List.of(), null);
        if (!nothing.unresolved().missing().equals(List.of(REAL, GHOST))) {
            helper.fail("a batch that found nothing did not call both missing: " + nothing.unresolved());
            return;
        }
        helper.succeed();
    }

    /** An outage is not evidence a card does not exist: everything asked is unavailable. */
    @GameTest(template = "tables")
    public static void afailedlookupcallseverythingunavailableandnothingmissing(GameTestHelper helper) {
        var answer = CardMetadataRequests.answerFor(
                List.of(REAL, GHOST), null, new java.io.IOException("429 Too Many Requests"));
        if (!answer.found().isEmpty() || !answer.unresolved().missing().isEmpty()) {
            helper.fail("a failed lookup claimed an answer: " + answer);
            return;
        }
        if (!answer.unresolved().unavailable().equals(List.of(REAL, GHOST))) {
            helper.fail("a failed lookup did not call its printings unavailable: " + answer.unresolved());
            return;
        }
        helper.succeed();
    }

    /**
     * What every screen writes in place of a name: loading, unknown, or unavailable - and a
     * name that turns up later settles it. A missing card is not asked about again for a
     * while; an unavailable one always is.
     */
    @GameTest(template = "tables")
    public static void thecachesaysloadingunknownorunavailable(GameTestHelper helper) {
        ClientCardCache cache = ClientCardCache.get();
        cache.clear();
        try {
            long now = 1_000L;
            if (!said(cache.unnamed(GHOST, now), "screen.gathering.deck.loading_card")) {
                helper.fail("a card nobody has answered for did not say it was loading");
                return;
            }
            cache.acceptUnresolved(new CardsUnresolvedPayload(List.of(GHOST), List.of(REAL)), now);
            if (!said(cache.unnamed(GHOST, now), "screen.gathering.deck.missing_card")) {
                helper.fail("a missing card still said " + cache.unnamed(GHOST, now));
                return;
            }
            if (!said(cache.unnamed(REAL, now), "screen.gathering.deck.unavailable_card")) {
                helper.fail("an unavailable card still said " + cache.unnamed(REAL, now));
                return;
            }
            if (!cache.alreadyAnsweredMissing(GHOST, now)
                    || cache.alreadyAnsweredMissing(REAL, now)) {
                helper.fail("the sweep would re-ask a missing card or skip an unavailable one");
                return;
            }
            long later = now + UnresolvedCards.MISSING_FOR_MILLIS;
            if (cache.alreadyAnsweredMissing(GHOST, later)) {
                helper.fail("no such card was believed past its expiry");
                return;
            }
            cache.accept(List.of(CardSummary.of(card(REAL))));
            if (!said(cache.unnamed(REAL, now), "screen.gathering.deck.loading_card")
                    || cache.summary(REAL).isEmpty()) {
                helper.fail("a name arriving after an outage did not settle the card");
                return;
            }
            cache.clear();
            if (cache.alreadyAnsweredMissing(GHOST, now)) {
                helper.fail("a disconnect kept what the last server could not find");
                return;
            }
        } finally {
            cache.clear();
        }
        helper.succeed();
    }

    private static boolean said(net.minecraft.network.chat.Component text, String key) {
        return text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents
                translated && translated.getKey().equals(key);
    }

    private static CardMetadata card(UUID id) {
        return new CardMetadata(
                id, id, "Something", "{1}", 1.0, "Artifact", "",
                java.util.Set.of(), java.util.Set.of(), List.of(), "normal",
                "tst", "Test Set", "1", Rarity.COMMON,
                false, true, true, false, false, List.of("paper"),
                Map.of(), Map.of(), "https://scryfall.com/card/tst/1");
    }
}
