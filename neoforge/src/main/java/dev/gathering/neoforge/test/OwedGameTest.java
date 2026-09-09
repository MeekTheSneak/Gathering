package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardItem;
import dev.gathering.item.PackItem;
import dev.gathering.server.Owed;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the server owes somebody who was not there to take it.
 * <p>A booster leaves the hand before the cards come back, so a player who logs out during
 * that round trip used to lose the pack outright: the stack was already gone and the cards
 * were handed to nobody. Now what they are owed is written down and given to them when they
 * next join.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwedGameTest {

    private static final UUID BOLT = UUID.fromString("aaaaaaaa-1111-4111-8111-111111111111");

    /** Cards rolled for somebody who had gone are kept, and handed over when they come back. */
    @GameTest(template = "empty")
    public static void cardsWaitForAPlayerWhoHadGone(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Owed.forget(player.getUUID());

        Owed.cards(player.getUUID(), List.of(
                CardIdentity.ofPrinting(BOLT, false), CardIdentity.ofPrinting(BOLT, true)));

        if (Owed.waitingFor(player.getUUID()) != 2) {
            helper.fail("Two cards were owed and " + Owed.waitingFor(player.getUUID())
                    + " were written down");
            return;
        }

        Owed.deliver(player);

        int cards = countOf(player, CardItem.class);
        if (cards != 2) {
            helper.fail("Two owed cards were handed over as " + cards);
            return;
        }
        if (Owed.waitingFor(player.getUUID()) != 0) {
            helper.fail("The list still owes something after it was handed over");
            return;
        }
        helper.succeed();
    }

    /** A pack that could not be opened after it was consumed is owed as a pack. */
    @GameTest(template = "empty")
    public static void apackThatWasNeverOpenedComesBack(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Owed.forget(player.getUUID());

        Owed.aPack(player.getUUID(), "DMU", "draft");
        Owed.deliver(player);

        if (countOf(player, PackItem.class) != 1) {
            helper.fail("A pack the server owed was not handed back");
            return;
        }
        helper.succeed();
    }

    /** Nothing owed is nothing said, so joining is not a message about an empty list. */
    @GameTest(template = "empty")
    public static void nothingOwedIsNothingDone(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Owed.forget(player.getUUID());

        Owed.deliver(player);

        if (countOf(player, CardItem.class) != 0 || countOf(player, PackItem.class) != 0) {
            helper.fail("A player owed nothing was handed something");
            return;
        }
        helper.succeed();
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
