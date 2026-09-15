package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardItem;
import dev.gathering.item.PackItem;
import dev.gathering.server.Owed;
import dev.gathering.server.PackWrappers;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A pack opened by hand: its cards are the player's from the moment they are drawn, and come into the
 * inventory when the wrapper is torn - never twice, never to anybody else, and never lost to a
 * disconnect or a crash in between.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PackWrapperGameTest {

    private static final UUID BOLT = UUID.fromString("bbbbbbbb-2222-4222-8222-222222222222");
    private static final UUID SHOCK = UUID.fromString("cccccccc-3333-4333-8333-333333333333");

    @GameTest(template = "empty")
    public static void theCardsComeWhenTheWrapperIsTornAndOnlyThen(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Owed.forget(player.getUUID());
        String token = PackWrappers.hold(player, null, "tst", null, cards());
        if (token == null) {
            helper.fail("the wrapper could not be written down");
            return;
        }
        if (countOf(player, CardItem.class) != 0) {
            helper.fail("the cards were in the inventory before the pack was torn");
            return;
        }
        if (Owed.waitingFor(player.getUUID()) != 2) {
            helper.fail("two wrapped cards were written down as " + Owed.waitingFor(player.getUUID()));
            return;
        }
        PackWrappers.torn(player, token);
        PackWrappers.torn(player, token);
        if (countOf(player, CardItem.class) != 2) {
            helper.fail("tearing the wrapper, twice, handed over " + countOf(player, CardItem.class) + " cards, not 2");
            return;
        }
        if (Owed.waitingFor(player.getUUID()) != 0) {
            helper.fail("the torn wrapper's cards are still written down as owed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nobodyTearsSomebodyElsesWrapper(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        Owed.forget(owner.getUUID());
        Owed.forget(other.getUUID());
        String token = PackWrappers.hold(owner, null, "tst", null, cards());
        PackWrappers.torn(other, token);
        if (countOf(other, CardItem.class) != 0 || countOf(owner, CardItem.class) != 0) {
            helper.fail("a wrapper was torn by somebody else's token");
            return;
        }
        PackWrappers.torn(owner, token);
        if (countOf(owner, CardItem.class) != 2) {
            helper.fail("the owner could not tear their own wrapper after somebody else tried");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPlayerWhoLeavesBeforeTearingGetsTheCardsOnJoining(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Owed.forget(player.getUUID());
        String token = PackWrappers.hold(player, null, "tst", null, cards());
        PackWrappers.forget(player.getUUID());
        // Joining again.
        Owed.deliver(player);
        if (countOf(player, CardItem.class) != 2) {
            helper.fail("a player who left with a pack unopened was handed " + countOf(player, CardItem.class) + " cards on joining");
            return;
        }
        PackWrappers.torn(player, token);
        if (countOf(player, CardItem.class) != 2) {
            helper.fail("a late tear after joining handed the cards over a second time");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theReceiptBecomesTheWrapperInOneWrite(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Owed.forget(player.getUUID());
        String receipt = Owed.opening(player.getUUID(), "tst", "play", "").orElseThrow();
        String token = PackWrappers.hold(player, receipt, "tst", null, cards());
        if (token == null) {
            helper.fail("the wrapper could not replace the receipt");
            return;
        }
        // The receipt is gone and the cards took its place: joining now hands over two cards and no pack.
        PackWrappers.forget(player.getUUID());
        Owed.deliver(player);
        if (countOf(player, PackItem.class) != 0 || countOf(player, CardItem.class) != 2) {
            helper.fail("after the wrapper replaced the receipt, joining handed over " + countOf(player, PackItem.class)
                    + " pack(s) and " + countOf(player, CardItem.class) + " card(s)");
            return;
        }
        helper.succeed();
    }

    private static List<CardIdentity> cards() {
        return List.of(CardIdentity.ofPrinting(BOLT, false), CardIdentity.ofPrinting(SHOCK, true));
    }

    private static int countOf(ServerPlayer player, Class<?> kind) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && kind.isInstance(stack.getItem())) {
                found += stack.getCount();
            }
        }
        return found;
    }
}
