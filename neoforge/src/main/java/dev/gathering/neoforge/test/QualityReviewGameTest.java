package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.*;
import dev.gathering.client.ClientSettings;
import dev.gathering.client.Tutorial;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.tutorial.TutorialStep;
import dev.gathering.item.*;
import dev.gathering.server.Owed;
import dev.gathering.server.PlayerGone;
import dev.gathering.server.PracticeTable;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Review-only acceptance probes against 8aea9323. No production logic is changed. */
/*
 * These four came from outside this repository.
 *
 * An external review of 8aea932 wrote them as acceptance specifications for four defects it
 * had reproduced - a practice table eating an owned deck, a disconnect leaving a practice
 * game nobody could clear, a crash losing a starter booster's chosen color, and the tutorial
 * ignoring the first action after Restart. All four failed on that revision and all four pass
 * now. They are kept unaltered except where a production signature they mirror has changed.
 *
 * They are worth keeping for a reason beyond the four bugs: every one of them is a test this
 * project would not have written for itself, because each crosses two features that were
 * built separately and tested separately.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QualityReviewGameTest {
    private static BlockPos table(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }

    private static void start(GameTestHelper helper, ServerPlayer player, BlockPos origin) {
        if (PracticeTable.start(player, origin) != PracticeTable.Outcome.STARTED) {
            throw new AssertionError("Practice fixture did not start");
        }
    }

    @GameTest(template = "empty")
    public static void practiceMustNotDestroyAnOwnedDeck(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        BlockPos origin = table(helper);
        try {
            start(helper, player, origin);
            var session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
            var me = TableSessions.seatIdOf(helper.getLevel(), origin, player.getUUID()).orElseThrow();
            session.submit(new GameEvent.CardsDrawn(me, me, 20));
            if (VisibilityRules.viewFor(session.state(), new Viewer.Seated(me))
                    .seat(me).zone(Zone.LIBRARY).count() != 0) {
                throw new AssertionError("Fixture must empty the practice library first");
            }
            // A physical owned deck. Custom paper avoids all external metadata/network waits.
            var owned = new DeckComponent("Owned before practice", "", Optional.empty(),
                    List.of(CardComponent.of(PaperStock.BLANK.identity())), List.of(), List.of());
            ItemStack stack = DeckItem.of(owned);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            TableBlock.putDown(helper.getLevel(), origin, player, stack);
            PracticeTable.stop(helper.getLevel(), origin);
            int remaining = 0;
            for (ItemStack item : player.getInventory().items) {
                if (DeckItem.deckOf(item).map(owned::equals).orElse(false)) remaining += item.getCount();
            }
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(origin).inflate(6))) {
                if (DeckItem.deckOf(item.getItem()).map(owned::equals).orElse(false)) {
                    remaining += item.getItem().getCount();
                }
            }
            if (remaining != 1) {
                helper.fail("Practice consumed an owned deck and returned " + remaining + "; expected 1");
                return;
            }
            helper.succeed();
        } finally {
            PracticeTable.stop(helper.getLevel(), origin);
        }
    }

    @GameTest(template = "empty")
    public static void disconnectMustCleanUpPractice(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        try {
            start(helper, player, origin);
            // The actual shared disconnect hook called by both loaders.
            PlayerGone.left(player);
            if (PracticeTable.isPracticeAt(helper.getLevel(), origin)
                    || TableSessions.hasSession(helper.getLevel(), origin)
                    || TableSeats.seatOf(helper.getLevel(), origin,
                            PracticeTable.demonstrationSeat()).isPresent()) {
                helper.fail("Disconnect left the practice session and demonstration seat behind");
                return;
            }
            helper.succeed();
        } finally {
            PracticeTable.stop(helper.getLevel(), origin);
        }
    }

    @GameTest(template = "empty")
    public static void recoveredStarterMustKeepItsChosenColor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        Owed.forget(player.getUUID());
        try {
            PackComponent original = new PackComponent("j25", "default", "W");
            // The exact receipt-writing boundary used by PackItem.use, before async work.
            // Do not launch a real network opening: simulate recovery with its receipt pending.
            if (Owed.opening(player.getUUID(), original.setCode(), original.kind(), original.color()).isEmpty()) {
                throw new AssertionError("Opening receipt was not saved");
            }
            Owed.deliver(player);
            PackComponent recovered = player.getInventory().items.stream()
                    .map(PackItem::packOf).flatMap(Optional::stream).findFirst().orElseThrow();
            if (!original.equals(recovered)) {
                helper.fail("Recovered booster changed from " + original + " to " + recovered);
                return;
            }
            helper.succeed();
        } finally {
            Owed.forget(player.getUUID());
        }
    }

    @GameTest(template = "empty")
    public static void firstDrawAfterRestartMustAdvanceTheTutorial(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        boolean offeredBefore = ClientSettings.tutorialOffered();
        try {
            start(helper, player, origin);
            var session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
            var me = TableSessions.seatIdOf(helper.getLevel(), origin, player.getUUID()).orElseThrow();
            var viewer = new Viewer.Seated(me);
            Tutorial.beginAt(origin, VisibilityRules.viewFor(session.state(), viewer));
            session.submit(new GameEvent.CardsDrawn(me, me, 1));
            Tutorial.sawBoard(origin, VisibilityRules.viewFor(session.state(), viewer));
            if (Tutorial.showing().orElseThrow() != TutorialStep.PLAY) {
                throw new AssertionError("The normal draw must advance before testing restart");
            }
            Tutorial.restart();
            session.submit(new GameEvent.CardsDrawn(me, me, 1));
            Tutorial.sawBoard(origin, VisibilityRules.viewFor(session.state(), viewer));
            if (Tutorial.showing().orElseThrow() != TutorialStep.PLAY) {
                helper.fail("The first confirmed draw after Restart was ignored; still showing "
                        + Tutorial.showing().orElseThrow());
                return;
            }
            helper.succeed();
        } finally {
            Tutorial.clear();
            ClientSettings.tutorialOffered(offeredBefore);
            PracticeTable.stop(helper.getLevel(), origin);
        }
    }
}
