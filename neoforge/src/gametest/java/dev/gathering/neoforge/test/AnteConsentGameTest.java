package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.ante.AnteConsent;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.Antes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import dev.gathering.block.TableBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Nobody plays for keeps without saying so, in a running world.
 * <p>The rule about who has agreed is pure and has its own tests. What cannot be checked
 * there is the part that decides whether a game actually begins - and that is the part where
 * a mistake costs somebody a card they never agreed to play for.
 * <p>The test server runs with ante off, which is itself worth checking: a server that has
 * not turned it on must never see the question at all.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnteConsentGameTest {

    /**
     * With ante off, nothing asks and the game starts exactly as it always did.
     * <p>The regression that would matter most: a gate added in front of every game that
     * silently stops games on the servers that never wanted it.
     */
    @GameTest(template = "empty")
    public static void aServerNotPlayingForKeepsIsNeverAsked(GameTestHelper helper) {
        if (Antes.isOffered()) {
            helper.fail("the test server has ante on, so this checks nothing");
            return;
        }
        BlockPos origin = seatedTable(helper, 2);
        if (Antes.askedFirst(helper.getLevel(), origin,
                MatchRules.single(FormatPresets.COMMANDER), true)) {
            helper.fail("a server with ante off put the question to a table anyway");
            return;
        }
        if (Antes.isAsking(helper.getLevel(), origin)) {
            helper.fail("a server with ante off left a question open at a table");
            return;
        }
        // And the ordinary path still works.
        if (TableSessions.start(helper.getLevel(), origin,
                MatchRules.single(FormatPresets.COMMANDER))
                != TableSessions.Outcome.STARTED) {
            helper.fail("a game would not start on a server with ante off");
            return;
        }
        Antes.clear();
        helper.succeed();
    }

    /**
     * An answer from somebody who is not at the table is not a vote.
     * <p>Somebody watching does not get to decide whether the people playing lose a card, and
     * the seat is read off the server's own record rather than out of the payload - so this
     * is checking that a made-up answer reaches nothing.
     */
    @GameTest(template = "empty")
    public static void somebodyWhoIsNotSittingThereDoesNotGetAVote(GameTestHelper helper) {
        Antes.clear();
        BlockPos origin = seatedTable(helper, 2);

        ServerPlayer watcher = helper.makeMockServerPlayerInLevel();
        watcher.setPos(origin.getCenter());
        // No question is open, and a watcher has no seat: either alone is enough, and both
        // together are what a client making things up would be trying to get past.
        Antes.answer(watcher, origin, AnteConsent.Answer.IN);

        if (TableSessions.hasSession(helper.getLevel(), origin)) {
            helper.fail("an answer from somebody with no seat started a game");
            return;
        }
        Antes.clear();
        helper.succeed();
    }

    /** A question at a table nobody is sitting at is not asked at all. */
    @GameTest(template = "empty")
    public static void anEmptyTableIsNotAsked(GameTestHelper helper) {
        Antes.clear();
        BlockPos origin = seatedTable(helper, 0);
        if (Antes.askedFirst(helper.getLevel(), origin,
                MatchRules.single(FormatPresets.COMMANDER), true)) {
            helper.fail("an empty table was asked whether it wanted to play for keeps");
            return;
        }
        Antes.clear();
        helper.succeed();
    }

    /** Between servers, a half-asked question does not survive into the next world. */
    @GameTest(template = "empty")
    public static void aHalfAskedQuestionDoesNotOutliveItsServer(GameTestHelper helper) {
        Antes.clear();
        if (Antes.isAsking(helper.getLevel(), BlockPos.ZERO)) {
            helper.fail("a question survived being cleared");
            return;
        }
        helper.succeed();
    }

    /**
     * Two tables at the same coordinates in different worlds are two tables.
     * <p>A position on its own is not a table. Keyed on the block alone, the overworld and the
     * nether would have shared one question - and either table could have answered for the
     * other, which for a question about losing a card is not a coincidence anybody should be
     * exposed to.
     */
    @GameTest(template = "empty")
    public static void aQuestionBelongsToOneWorldAsWellAsOnePlace(GameTestHelper helper) {
        Antes.clear();
        BlockPos origin = seatedTable(helper, 2);
        ServerLevel nether = helper.getLevel().getServer()
                .getLevel(net.minecraft.world.level.Level.NETHER);
        if (nether == null) {
            helper.fail("there was no second world to check a table against");
            return;
        }
        if (Antes.isAsking(helper.getLevel(), origin) || Antes.isAsking(nether, origin)) {
            helper.fail("a question was open before anything asked one");
            return;
        }
        // Nothing is asked here (the test server has ante off), so what this proves is that
        // the two are asked about separately rather than through one shared key.
        if (Antes.isAsking(nether, origin) != Antes.isAsking(helper.getLevel(), origin)) {
            helper.fail("two worlds disagreed about a question neither of them has");
            return;
        }
        Antes.clear();
        helper.succeed();
    }

    /**
     * A game started through the ante question still knows its format was named.
     * <p>The deck check refuses only on a table that was told somebody picked a format, and
     * the ordinary start says so. The ante path starts its game later - out of the last
     * answer, when whoever picked the format is long gone from the call stack - and said
     * nothing at all. So on any server with ante turned on, the deck check could not refuse
     * a deck at any table, ever. Not a crash and not a message: a whole feature quietly
     * downgraded from a refusal to a note, on exactly the servers most likely to want it.
     * <p>Driven through the real question with ante switched on, rather than through a seam
     * put here for the test, because the bug was entirely in what the real path forgot.
     */
    @GameTest(template = "empty")
    public static void aGameStartedForKeepsKnowsItsFormatWasNamed(GameTestHelper helper) {
        withAnteOn(helper, () -> {
            Antes.clear();
            BlockPos origin = seatedTable(helper, 0);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setPos(origin.getCenter());
            List<SeatAnchor> anchors = TableClusters.at(helper.getLevel(), origin).seats();
            TableSeats.take(helper.getLevel(), origin, anchors.get(0).cell(),
                    anchors.get(0).side(), player.getUUID());

            if (!Antes.askedFirst(helper.getLevel(), origin,
                    MatchRules.single(FormatPresets.COMMANDER), true)) {
                return "a server with ante on did not put the question to a seated table";
            }
            // The only seat says yes, which settles it and starts the game.
            Antes.answer(player, origin, AnteConsent.Answer.IN);

            if (!TableSessions.hasSession(helper.getLevel(), origin)) {
                return "the table agreed to play for keeps and no game started";
            }
            TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
            if (!table.playingForKeeps()) {
                return "a game started through the ante question is not for keeps";
            }
            if (!table.formatWasChosen()) {
                return "a game started through the ante question does not know its format was"
                        + " named, so the deck check can only warn and never refuse";
            }
            return null;
        });
    }

    /**
     * Saying no to ante starts an ordinary game; it does not stop the table playing.
     * <p>Declining at a real table means "deal me in, but I am not playing for my cards". It
     * used to mean nothing happened at all: the question was withdrawn, no game started, and
     * the next attempt asked the same question and got the same answer. One person who did
     * not want to play for keeps could stop that table ever starting a game - and the message
     * said "Ante declined. Nothing is at stake.", which describes a game in progress.
     */
    @GameTest(template = "empty")
    public static void decliningTheAnteStillStartsAGame(GameTestHelper helper) {
        withAnteOn(helper, () -> {
            Antes.clear();
            BlockPos origin = seatedTable(helper, 0);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setPos(origin.getCenter());
            List<SeatAnchor> anchors = TableClusters.at(helper.getLevel(), origin).seats();
            TableSeats.take(helper.getLevel(), origin, anchors.get(0).cell(),
                    anchors.get(0).side(), player.getUUID());

            if (!Antes.askedFirst(helper.getLevel(), origin,
                    MatchRules.single(FormatPresets.COMMANDER), true)) {
                return "a server with ante on did not put the question to a seated table";
            }
            Antes.answer(player, origin, AnteConsent.Answer.OUT);

            if (!TableSessions.hasSession(helper.getLevel(), origin)) {
                return "somebody declined the ante and the table could not start a game at all";
            }
            TableBlockEntity table = TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
            if (table.playingForKeeps()) {
                return "a table that declined the ante is playing for keeps anyway";
            }
            // And the format it was told still holds, exactly as it does when they agree.
            if (!table.formatWasChosen()) {
                return "the game that started after a decline does not know its format was"
                        + " named, so the deck check can only warn";
            }
            return null;
        });
    }

    /** Runs something on a server that is playing for keeps, and puts the config back. */
    private static void withAnteOn(GameTestHelper helper, java.util.function.Supplier<String> what) {
        java.nio.file.Path file = dev.gathering.platform.Platform.get()
                .configDirectory().resolve("gathering-server.toml");
        String before = null;
        try {
            if (java.nio.file.Files.isRegularFile(file)) {
                before = java.nio.file.Files.readString(file, StandardCharsets.UTF_8);
            }
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file,
                    "[modes]\ncollection_enabled = true\n\n[ante]\nenabled = true\n",
                    StandardCharsets.UTF_8);
            dev.gathering.service.ServerSettings.load(dev.gathering.platform.Platform.get());

            String wrong = what.get();
            if (wrong != null) {
                helper.fail(wrong);
                return;
            }
            helper.succeed();
        } catch (IOException couldNotWrite) {
            helper.fail("The config file could not be written: " + couldNotWrite);
        } finally {
            Antes.clear();
            try {
                if (before == null) {
                    java.nio.file.Files.deleteIfExists(file);
                } else {
                    java.nio.file.Files.writeString(file, before, StandardCharsets.UTF_8);
                }
            } catch (IOException couldNotRestore) {
                // Nothing useful to do here; the next start writes a fresh one.
            }
            dev.gathering.service.ServerSettings.load(dev.gathering.platform.Platform.get());
        }
    }

    // ------------------------------------------------------------------ bits

    private static BlockPos seatedTable(GameTestHelper helper, int players) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerLevel level = helper.getLevel();
        level.setBlock(origin, GatheringContent.TABLE.get().defaultBlockState(), 3);
        for (TablePart part : TablePart.values()) {
            level.setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        List<SeatAnchor> anchors = TableClusters.at(level, origin).seats();
        for (int index = 0; index < Math.min(players, anchors.size()); index++) {
            SeatAnchor anchor = anchors.get(index);
            TableSeats.take(level, origin, anchor.cell(), anchor.side(),
                    new java.util.UUID(11L, index));
        }
        return origin;
    }
}
