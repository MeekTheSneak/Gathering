package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.client.ClientSettings;
import dev.gathering.client.PendingWork;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the screen is allowed to say about a request it has not been answered about.
 * <p>Three rules, and each of them has been got wrong somewhere in this mod already:
 * an answer to somebody else's press cannot finish this one; nothing unanswered is reported as
 * refused; and nothing here resends a thing by itself.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PendingWorkGameTest {

    /** An answer meant for another request cannot finish this one. */
    @GameTest(template = "empty")
    public static void anotherrequestsanswercannotfinishthisone(GameTestHelper helper) {
        PendingWork.clear();
        try {
            UUID mine = PendingWork.sent();
            UUID theirs = PendingWork.sent();
            PendingWork.confirmed(theirs);
            PendingWork.State state = PendingWork.of(mine).orElseThrow().state();
            if (state != PendingWork.State.WAITING) {
                helper.fail("another request's confirmation left mine as " + state);
                return;
            }
            helper.succeed();
        } finally {
            PendingWork.clear();
        }
    }

    /**
     * An id nothing is waiting for is ignored rather than remembered.
     * <p>A stale acknowledgment has nothing to finish. Making an entry for it would be
     * inventing a request the player never made, and it would grow without end on a client
     * that had been fed them.
     */
    @GameTest(template = "empty")
    public static void ananswertonorequestisignored(GameTestHelper helper) {
        PendingWork.clear();
        try {
            PendingWork.confirmed(UUID.randomUUID());
            PendingWork.refused(UUID.randomUUID(), Component.literal("no"));
            if (PendingWork.remembered() != 0) {
                helper.fail("an answer to no request was recorded as one: "
                        + PendingWork.remembered() + " remembered");
                return;
            }
            helper.succeed();
        } finally {
            PendingWork.clear();
        }
    }

    /**
     * Nothing came back is not the same as it was refused.
     * <p>The one that matters. A screen that says "failed" after a timeout is telling the
     * player something it does not know, and a player told their booster failed is a player
     * who opens another one.
     */
    @GameTest(template = "empty")
    public static void silenceisnotrefusal(GameTestHelper helper) throws Exception {
        PendingWork.clear();
        try {
            UUID id = PendingWork.sent();
            // Past the largest threshold the setting allows, rather than past whatever it
            // happens to be. Reading the live setting made this test depend on a global
            // another test in the same run is allowed to change.
            Thread.sleep(dev.gathering.client.ClientSettings.LATEST_WAITING_NOTICE + 60L);
            PendingWork.Work work = PendingWork.of(id).orElseThrow();
            if (work.state() == PendingWork.State.REFUSED) {
                helper.fail("a request nobody answered was reported as refused");
                return;
            }
            if (work.state() != PendingWork.State.UNKNOWN) {
                helper.fail("a request left waiting past the threshold reads as " + work.state());
                return;
            }
            if (!PendingWork.worthMentioning(id) && work.state() == PendingWork.State.WAITING) {
                helper.fail("a request past the threshold is not worth mentioning");
                return;
            }
            helper.succeed();
        } finally {
            PendingWork.clear();
        }
    }

    /**
     * A confirmation that arrives after the wait ran out still lands.
     * <p>"Unknown" is this client's state of knowledge, not a decision about the request. The
     * server may well answer a moment later, and when it does the answer is the truth.
     */
    @GameTest(template = "empty")
    public static void alateanswerstillcounts(GameTestHelper helper) throws Exception {
        PendingWork.clear();
        try {
            UUID id = PendingWork.sent();
            Thread.sleep(dev.gathering.client.ClientSettings.LATEST_WAITING_NOTICE + 60L);
            if (PendingWork.of(id).orElseThrow().state() != PendingWork.State.UNKNOWN) {
                helper.fail("the fixture did not reach the unknown state");
                return;
            }
            PendingWork.confirmed(id);
            if (PendingWork.of(id).orElseThrow().state() != PendingWork.State.CONFIRMED) {
                helper.fail("an answer that arrived after the wait ran out was thrown away");
                return;
            }
            helper.succeed();
        } finally {
            PendingWork.clear();
        }
    }

    /** Forgetting a request is a screen closing, and says nothing about the work itself. */
    @GameTest(template = "empty")
    public static void forgettingarequestisnotcancelling(GameTestHelper helper) {
        PendingWork.clear();
        try {
            UUID id = PendingWork.sent();
            PendingWork.forget(id);
            if (PendingWork.of(id).isPresent()) {
                helper.fail("a forgotten request is still remembered");
                return;
            }
            // And the answer, when it comes, has nothing to land on rather than reviving it.
            PendingWork.confirmed(id);
            if (PendingWork.of(id).isPresent()) {
                helper.fail("an answer brought a forgotten request back");
                return;
            }
            helper.succeed();
        } finally {
            PendingWork.clear();
        }
    }

    /** A client fed answers to nothing does not grow without end. */
    @GameTest(template = "empty")
    public static void themapisbounded(GameTestHelper helper) {
        PendingWork.clear();
        try {
            for (int sent = 0; sent < 500; sent++) {
                PendingWork.sent();
            }
            if (PendingWork.remembered() > 64) {
                helper.fail("five hundred requests left " + PendingWork.remembered()
                        + " remembered, past the bound of 64");
                return;
            }
            helper.succeed();
        } finally {
            PendingWork.clear();
        }
    }
    /**
     * A young request says nothing, an overdue one says it does not know, and neither says
     * failed.
     * <p>This is the policy the screens draw, kept in {@link PendingWork} precisely so that it
     * can be run: a screen cannot be loaded here at all, because a dedicated server refuses
     * every client class one is built from. Before this existed, both screens that wait on the
     * server waited for ever - the button went inactive on the press and came back only when
     * an answer arrived, so a reply that never came left a dead button and no reason.
     */
    @GameTest(template = "empty")
    public static void anoverduerequestsayssoandneversaysfailed(GameTestHelper helper) {
        PendingWork.clear();
        int patience = ClientSettings.waitingAfterMillis();
        try {
            // The most patient this setting goes. The threshold is the player's and it is
            // clamped, so a test that asked for a minute got three seconds and one that asked
            // for nothing got a tenth of one - which is why these are the named bounds rather
            // than numbers that look convincing.
            ClientSettings.waitingAfterMillis(ClientSettings.LATEST_WAITING_NOTICE);
            UUID id = PendingWork.sent();
            if (PendingWork.noteFor(id).isPresent()) {
                helper.fail("a request that has only just gone out already had something to say");
                return;
            }

            // Now the least patient it goes, and past it. This wait is a lower bound on a
            // monotonic elapsed time rather than a race with another thread: the clock only
            // moves one way, so sleeping longer than the threshold cannot make the answer
            // wrong, and nothing else has to happen for it to become true.
            ClientSettings.waitingAfterMillis(ClientSettings.SOONEST_WAITING_NOTICE);
            Thread.sleep(ClientSettings.SOONEST_WAITING_NOTICE + 50L);

            Component said = PendingWork.noteFor(id).orElse(null);
            if (said == null) {
                helper.fail("an overdue request said nothing at all");
                return;
            }
            if (PendingWork.of(id).orElseThrow().state() != PendingWork.State.UNKNOWN) {
                helper.fail("an overdue request was left as "
                        + PendingWork.of(id).orElseThrow().state() + " rather than UNKNOWN");
                return;
            }
            if (PendingWork.of(id).orElseThrow().state() == PendingWork.State.REFUSED) {
                helper.fail("silence was reported as a refusal, which is the one thing it is not");
                return;
            }

            // And an answer that does arrive, late, still settles it - so the note goes away
            // rather than standing over a result the screen is about to show.
            PendingWork.confirmed(id);
            if (PendingWork.noteFor(id).isPresent()) {
                helper.fail("a confirmed request went on saying it had no answer");
                return;
            }
            helper.succeed();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            helper.fail("the wait for the threshold was interrupted");
        } finally {
            ClientSettings.waitingAfterMillis(patience);
            PendingWork.clear();
        }
    }

    /**
     * A refusal shows the server's own words rather than a shrug.
     * <p>The difference between this and the test above is the whole reason the four states
     * exist: one of them is the server having decided, and the other is nobody knowing.
     */
    @GameTest(template = "empty")
    public static void arefusalshowsthereasonitcamewith(GameTestHelper helper) {
        PendingWork.clear();
        try {
            UUID id = PendingWork.sent();
            PendingWork.refused(id, Component.literal("line 4: no such card"));
            Component said = PendingWork.noteFor(id).orElse(null);
            if (said == null) {
                helper.fail("a refusal said nothing");
                return;
            }
            if (!said.getString().contains("line 4")) {
                helper.fail("a refusal lost the reason it came with and said: " + said.getString());
                return;
            }
            helper.succeed();
        } finally {
            PendingWork.clear();
        }
    }
}
