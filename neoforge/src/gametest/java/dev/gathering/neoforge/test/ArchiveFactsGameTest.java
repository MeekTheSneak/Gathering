package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.booster.ArchiveAudit;
import dev.gathering.server.ArchiveFacts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the archive's audit keeps about each set comes back as it went, and is read again when it
 * should be.
 * <p>The audit reads every set Magic has had. It is only affordable because a set is read once and
 * kept - so a file that came back different would be a wrong archive every start after the first,
 * and a file trusted when the set had grown would be an archive missing the cards added since.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchiveFactsGameTest {

    private ArchiveFactsGameTest() {
    }

    @GameTest(template = "empty")
    public static void aSetsFactsComeBackAndGoStaleWhenTheyShould(GameTestHelper helper) throws Exception {
        Path root = Files.createTempDirectory("gathering-archive-facts-");
        try {
            ArchiveAudit.SetFacts facts = new ArchiveAudit.SetFacts("tst",
                    List.of(new UUID(1, 1), new UUID(1, 2)), Set.of(new UUID(1, 1)), Set.of(new UUID(1, 2)), true);
            long now = System.currentTimeMillis();
            ArchiveFacts.write(root, new ArchiveFacts.Kept(facts, now, 2));

            ArchiveFacts.Kept back = ArchiveFacts.read(root, "tst").orElse(null);
            if (back == null || !back.facts().equals(facts) || back.cardCount() != 2) {
                helper.fail("a set's audit did not come back as it was kept: " + back);
                return;
            }
            if (!back.stillGood(2, now, true)) {
                helper.fail("a set's audit kept a moment ago, at the same size, was not trusted");
                return;
            }
            if (back.stillGood(3, now, true)) {
                helper.fail("a set that has grown was trusted from what was read before it grew");
                return;
            }
            if (back.stillGood(2, now + java.time.Duration.ofDays(31).toMillis(), true)) {
                helper.fail("a month-old audit was trusted");
                return;
            }
            ArchiveAudit.SetFacts unread = new ArchiveAudit.SetFacts("tst", facts.catalog(), Set.of(), Set.of(), false);
            if (new ArchiveFacts.Kept(unread, now, 2).stillGood(2, now, true)) {
                helper.fail("a set now drawn from was trusted without its boosters and products ever read");
                return;
            }
            if (ArchiveFacts.read(root, "../escape").isPresent()) {
                helper.fail("a set code that is not one reached a path");
                return;
            }
            helper.succeed();
        } finally {
            try (var files = Files.walk(root)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }
}
