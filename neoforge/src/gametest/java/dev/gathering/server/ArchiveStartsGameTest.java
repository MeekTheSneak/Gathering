package dev.gathering.server;

import dev.gathering.Gathering;
import dev.gathering.core.booster.ArchiveAudit;
import dev.gathering.core.card.SetRelease;
import dev.gathering.platform.Platform;
import dev.gathering.service.CollationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Which start of the archive an answer belongs to, checked where only this package can reach: the answer
 * {@link Archive#remainderOf} keeps for a family, and the point between reading its start and keeping it.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchiveStartsGameTest {

    /**
     * An answer begun under one start of the archive is never kept for the next.
     * <p>Found reviewing #56. remainderOf read which start it was under the publishing lock, let the lock go,
     * and only then kept its answer in the shared map. A restart in that gap - a settings change, on the
     * server thread, while remainderOf ran on a worker for a later candidate - emptied the map first, and the
     * old start's answer went into the new start's, where every opening of the family took it, worked out
     * under the old settings, until it finished. Here a restart is tried at exactly that point, from another
     * thread, as a settings change comes.
     */
    @GameTest(template = "empty")
    public static void anAnswerBegunBeforeARestartIsNotKeptForTheNextStart(GameTestHelper helper) throws Exception {
        CollationService collation = CollationService.active().orElse(null);
        if (collation == null || dev.gathering.service.CardDataService.active().isEmpty()) {
            // Without both, an audit is answered on the spot, empty - and an answer finished that soon is taken back
            // out of the map at once, so the test would pass whatever the start check did.
            helper.fail("fixture: no collation worker or card pipeline, so no audit would wait to be kept");
            return;
        }
        BlockPos here = helper.absolutePos(BlockPos.ZERO);
        String family = "zk" + Integer.toString(Math.floorMod(here.hashCode(), 36 * 36), 36);
        UUID only = UUID.randomUUID();
        SetRelease set = new SetRelease(family, "Archive test", "expansion", "2020-01-01", false, 1, 1);
        Path root = Platform.get().dataDirectory();
        // Kept facts that still hold, so the audits this queues read them off disk rather than ask a network.
        ArchiveFacts.write(root, new ArchiveFacts.Kept(
                new ArchiveAudit.SetFacts(family, List.of(only), Set.of(only), Set.of(), true),
                System.currentTimeMillis(), 1));
        CountDownLatch release = new CountDownLatch(1);
        Thread[] restart = new Thread[1];
        String wrong = null;
        try {
            holdTheWorker(collation, release);
            Archive.membersForTesting(Map.of(family, List.of(set)));
            Archive.beforeKeepingForTesting = () -> {
                restart[0] = new Thread(() -> Archive.membersForTesting(Map.of(family, List.of(set))),
                        "archive restart");
                restart[0].start();
                try {
                    // Long enough for the restart to finish, if nothing holds it back.
                    restart[0].join(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            };
            CompletableFuture<Archive.Audited> first;
            try {
                first = Archive.remainderOf(family);
            } finally {
                Archive.beforeKeepingForTesting = null;
            }
            if (restart[0] == null) {
                wrong = "fixture: working out " + family + " never reached the point between its start and its answer";
            } else if (first.isDone()) {
                wrong = "fixture: the answer for " + family + " was worked out at once, behind a held worker, so whether"
                        + " it would be kept for the next start was not seen";
            } else {
                restart[0].join(TimeUnit.SECONDS.toMillis(10));
                if (restart[0].isAlive()) {
                    wrong = "fixture: the restart of the archive never finished";
                } else if (Archive.remainderOf(family) == first) {
                    wrong = "an answer begun before the archive started again was kept for the new start: the next"
                            + " opening of " + family + " took it rather than working the family out again";
                }
            }
        } finally {
            Archive.beforeKeepingForTesting = null;
            release.countDown();
            Archive.clear();
            Files.deleteIfExists(root.resolve("archive-audit").resolve(family + ".facts"));
        }
        if (wrong != null) {
            helper.fail(wrong);
            return;
        }
        helper.succeed();
    }

    /** Keeps the one collation worker busy until the latch is let go, so what is queued behind it waits. */
    private static void holdTheWorker(CollationService collation, CountDownLatch release) {
        CompletableFuture.runAsync(() -> {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, collation.worker());
    }
}
