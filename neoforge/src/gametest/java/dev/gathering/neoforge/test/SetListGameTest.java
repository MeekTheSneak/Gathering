package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.net.HttpTransport;
import dev.gathering.core.net.RateLimiter;
import dev.gathering.core.scryfall.ScryfallClient;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.server.SetNames;
import dev.gathering.service.CardDataService;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The list of every set is asked for once, and never by naming a pack.
 * <p>From the 2026-09-25 sweep (#25). Every ask for the list while it was missing queued a download of
 * its own on the one card worker: a default start asks four times on one tick before the first reply,
 * and a server that could not reach Scryfall queued another megabyte of it for every pack made - a
 * loot roll, a shopkeeper's shelf - because naming the pack asked. Each of those was four attempts
 * long, and each was downloaded again when Scryfall came back.
 * <p>On a card service of the test's own, whose worker holds what it is given rather than running it,
 * so what was asked for can be counted without reaching Scryfall. The running server's service is the
 * one {@code SetNames} reads, so it is this one for exactly as long as the eleven names take.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SetListGameTest {

    private SetListGameTest() {
    }

    @GameTest(template = "empty")
    public static void theSetListIsAskedForOnceAndNeverByNamingAPack(GameTestHelper helper) throws Exception {
        var made = CardDataService.class.getDeclaredConstructor(Path.class, String.class);
        made.setAccessible(true);
        CardDataService service = made.newInstance(Files.createTempDirectory("gathering-set-list-"), "audit/no-network");
        Field executor = CardDataService.class.getDeclaredField("executor");
        executor.setAccessible(true);
        ((ExecutorService) executor.get(service)).shutdownNow();
        var held = new ReviewRoundTwoGameTest.HeldExecutor();
        executor.set(service, held);
        Field active = CardDataService.class.getDeclaredField("active");
        active.setAccessible(true);
        String wrong = null;
        try {
            // The four a default start makes on one tick: the current set, the loot pool's sets, the
            // archive, and the archive again through the sets in play.
            for (int ask = 0; ask < 4; ask++) {
                service.allSets();
            }
            if (held.queued.size() != 1) {
                wrong = "four asks for the set list before any answer queued " + held.queued.size()
                        + " downloads of it, not one";
            }

            if (wrong == null) {
                wrong = nameElevenThingsWith(service, active, held, 1, "while the list was on its way");
            }

            if (wrong == null) {
                // And a download that failed is believed for a while: the one held is run against a
                // Scryfall that cannot be reached, once, and the list asked for again straight after.
                Field client = CardDataService.class.getDeclaredField("client");
                client.setAccessible(true);
                client.set(service, new ScryfallClient(unreachable(), new RateLimiter(0L, System::currentTimeMillis,
                        ignored -> { }), "audit/no-network", ScryfallClient.DEFAULT_BASE_URL, 1, ignored -> { }));
                held.queued.remove(0).run();
                CompletableFuture<Map<String, dev.gathering.core.card.SetRelease>> again = service.allSets();
                if (!again.isCompletedExceptionally()) {
                    wrong = "the set list was asked for again the moment a download of it failed";
                } else if (!held.queued.isEmpty()) {
                    wrong = "a set list that failed a moment ago queued " + held.queued.size() + " more downloads";
                } else if (service.knownSets() != null) {
                    wrong = "a set list that failed to arrive is known: " + service.knownSets().size() + " sets";
                }
            }

            if (wrong == null) {
                // And named while the failure is believed, which is when asking hands back a failure: a
                // name that waited on it would throw there, out of a loot roll.
                wrong = nameElevenThingsWith(service, active, held, 0, "just after the list failed to arrive");
            }
        } finally {
            service.close();
        }
        if (wrong != null) {
            helper.fail(wrong);
            return;
        }
        helper.succeed();
    }

    /**
     * Ten packs made and one set named, with this service as the server's, and what went wrong.
     *
     * @param stillHeld how many downloads the service's worker should be holding afterwards
     */
    private static String nameElevenThingsWith(CardDataService service, Field active,
            ReviewRoundTwoGameTest.HeldExecutor held, int stillHeld, String when) throws Exception {
        Object running = active.get(null);
        active.set(null, service);
        try {
            for (int pack = 0; pack < 10; pack++) {
                PackItem.of(new PackComponent("blb", "play"));
            }
            SetNames.of("blb");
        } catch (RuntimeException threw) {
            return "naming a pack " + when + " threw " + threw;
        } finally {
            active.set(null, running);
        }
        return held.queued.size() == stillHeld ? null
                : "naming ten packs and a set " + when + " queued " + (held.queued.size() - stillHeld)
                        + " more downloads of the set list";
    }

    /** A Scryfall that cannot be reached: every request fails as a refused connection does. */
    private static HttpTransport unreachable() {
        return new HttpTransport() {
            @Override
            public HttpReply get(String url, Map<String, String> headers) throws IOException {
                throw new java.net.ConnectException("Connection refused");
            }

            @Override
            public HttpReply post(String url, String body, Map<String, String> headers) throws IOException {
                throw new java.net.ConnectException("Connection refused");
            }
        };
    }
}
