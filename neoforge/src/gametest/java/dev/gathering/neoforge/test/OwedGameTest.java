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

        Owed.aPack(player.getUUID(), "DMU", "draft", "");
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

    /**
     * A line this version cannot read stays owed rather than being swept away with the rest.
     * <p>Delivery used to clear the whole file the moment it had handed over what it
     * understood, so one line written by a different version of this mod - a kind of thing
     * that did not exist yet, or no longer does - took everything else on the list with it.
     * That is somebody's property being deleted for being unfamiliar.
     */
    @GameTest(template = "empty")
    public static void alineThisVersionCannotReadIsNotThrownAway(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Owed.forget(player.getUUID());

        Owed.aPack(player.getUUID(), "DMU", "draft", "");
        java.nio.file.Path list = dev.gathering.server.ServerRun.inSave("owed")
                .orElseThrow().resolve(player.getUUID() + ".txt");
        try {
            java.nio.file.Files.writeString(list,
                    java.nio.file.Files.readString(list) + System.lineSeparator()
                            + "relic 9d2f from-a-later-version");
        } catch (java.io.IOException couldNotWrite) {
            helper.fail("Could not write the owed list to test it: " + couldNotWrite.getMessage());
            return;
        }

        Owed.deliver(player);

        if (countOf(player, PackItem.class) != 1) {
            helper.fail("The pack on a list with an unreadable line beside it was not handed over");
            return;
        }
        if (Owed.waitingFor(player.getUUID()) != 1) {
            helper.fail("The line this version cannot read was left owed "
                    + Owed.waitingFor(player.getUUID()) + " times, not once");
            return;
        }
        Owed.forget(player.getUUID());
        helper.succeed();
    }

    /**
     * A safety ceiling must not be a way to delete cards somebody already earned.
     * <p>The list used to keep the newest two thousand and forty-eight entries and drop
     * whatever was older. An audit recorded two thousand and forty-nine owed cards and found
     * two thousand and forty-eight waiting: the guard against a runaway file was itself
     * deleting property. A list this long is a fault somewhere else, and the answer to a
     * fault is to say so, not to start throwing cards away.
     */
    @GameTest(template = "empty")
    public static void theceilingDoesNotDeleteWhatIsAlreadyOwed(GameTestHelper helper) {
        java.util.UUID who = java.util.UUID.randomUUID();
        try {
            Owed.cards(who, java.util.Collections.nCopies(2049,
                    CardIdentity.ofPrinting(java.util.UUID.randomUUID(), false)));

            int waiting = Owed.waitingFor(who);
            if (waiting != 2049) {
                helper.fail("2049 cards were owed and " + waiting + " were kept");
                return;
            }
            helper.succeed();
        } finally {
            Owed.forget(who);
        }
    }

    /**
     * What is owed is written inside the save, not beside the game.
     * <p>Two single-player worlds in one installation share a game directory. While this list
     * lived there they shared it too, so a booster interrupted in one world could be claimed
     * on joining the other - and was then gone from the world that owed it.
     */
    @GameTest(template = "empty")
    public static void whatIsOwedBelongsToTheSave(GameTestHelper helper) {
        java.util.UUID who = java.util.UUID.randomUUID();
        try {
            Owed.aPack(who, "DMU", "draft", "");

            java.nio.file.Path list = dev.gathering.server.ServerRun.inSave("owed")
                    .orElseThrow().resolve(who + ".txt");
            if (!java.nio.file.Files.isRegularFile(list)) {
                helper.fail("What is owed was not written inside the save: " + list);
                return;
            }
            java.nio.file.Path save = helper.getLevel().getServer()
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath().normalize();
            if (!list.toAbsolutePath().normalize().startsWith(save)) {
                helper.fail("What is owed was written at " + list + ", outside the save at " + save);
                return;
            }
            helper.succeed();
        } finally {
            Owed.forget(who);
        }
    }

    /**
     * A write that could not happen is reported as one, not logged and forgotten.
     * <p>The caller is about to have consumed somebody's booster. If the receipt for it did
     * not reach the disk, it must not be told that the property was safeguarded - that is the
     * difference between a recoverable interruption and a card that simply stops existing.
     * <p>Blocked by putting a directory where the half-written file has to go, which is a
     * spot belonging to this one player and to nothing else. Blocking the whole owed folder
     * would work too and would depend on this test running before every other one that owes
     * anybody anything, which is not a thing a test may rely on.
     */
    @GameTest(template = "empty")
    public static void awriteThatCannotHappenIsSaidSo(GameTestHelper helper) {
        java.util.UUID who = java.util.UUID.randomUUID();
        java.nio.file.Path inTheWay;
        try {
            java.nio.file.Path folder = dev.gathering.server.ServerRun.inSave("owed").orElseThrow();
            java.nio.file.Files.createDirectories(folder);
            inTheWay = folder.resolve(who + ".txt.writing");
            java.nio.file.Files.createDirectory(inTheWay);
        } catch (java.io.IOException couldNotSetUp) {
            helper.fail("Could not set the fixture up: " + couldNotSetUp.getMessage());
            return;
        }
        try {
            if (Owed.aPack(who, "DMU", "draft", "")) {
                helper.fail("A pack that could not be written down was reported as safeguarded");
                return;
            }
            if (Owed.waitingFor(who) != 0) {
                helper.fail("A pack that could not be written down is somehow on the list");
                return;
            }
            helper.succeed();
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(inTheWay);
                Owed.forget(who);
            } catch (java.io.IOException leaveIt) {
                helper.fail("Could not clear the fixture: " + leaveIt.getMessage());
            }
        }
    }

    /**
     * A pack consumed by a server that never finished opening it comes back, exactly once.
     * <p>The window this exists for. A booster leaves the hand before the opening starts,
     * because a pack still in the hand when the cards arrive is a pack that can be opened
     * twice. Everything after that is a round trip, and the debt used to be written from the
     * far end of it - which covers a player logging out and covers nothing else. A server
     * stopped in the middle cancels the queued work, the completion never runs, and there is
     * no record anywhere that a booster ever existed.
     */
    @GameTest(template = "empty")
    public static void apackTakenAndNeverOpenedComesBack(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        Owed.forget(player.getUUID());

        // The receipt, written before the pack is consumed. Then nothing else happens,
        // which is what a server stopped mid-opening looks like from the disk's side.
        String receipt = Owed.opening(player.getUUID(), "DMU", "draft", "").orElse(null);
        if (receipt == null) {
            helper.fail("A pack about to be opened could not be written down at all");
            return;
        }

        Owed.deliver(player);

        if (countOf(player, PackItem.class) != 1) {
            helper.fail("A pack taken and never opened came back as "
                    + countOf(player, PackItem.class) + " packs");
            return;
        }
        // And only once: joining again does not hand out a second one.
        Owed.deliver(player);
        if (countOf(player, PackItem.class) != 1) {
            helper.fail("Joining twice handed out the same pack "
                    + countOf(player, PackItem.class) + " times");
            return;
        }
        helper.succeed();
    }

    /**
     * An opening that finished owes nothing, whichever way it finished.
     * <p>Settling replaces the receipt rather than adding to it, so a player who has their
     * cards is not also owed the pack they came out of - and a player who was handed the pack
     * back is not owed it twice.
     */
    @GameTest(template = "empty")
    public static void asettledOpeningOwesNothingMore(GameTestHelper helper) {
        java.util.UUID who = java.util.UUID.randomUUID();
        try {
            String receipt = Owed.opening(who, "DMU", "draft", "").orElseThrow();
            if (Owed.waitingFor(who) != 1) {
                helper.fail("A receipt was written and the list holds " + Owed.waitingFor(who));
                return;
            }

            // The player was there, so they have the cards: the receipt simply goes.
            Owed.settled(who, receipt, java.util.List.of());
            if (Owed.waitingFor(who) != 0) {
                helper.fail("A settled opening still owes " + Owed.waitingFor(who) + " thing(s)");
                return;
            }
            // Settling again is not a way to be owed anything.
            Owed.settled(who, receipt, java.util.List.of(CardIdentity.ofPrinting(BOLT, false)));
            if (Owed.waitingFor(who) != 0) {
                helper.fail("Settling a receipt twice owed " + Owed.waitingFor(who) + " card(s)");
                return;
            }

            // And the other ending: the player had gone, so the cards take the receipt's place.
            String second = Owed.opening(who, "DMU", "draft", "").orElseThrow();
            Owed.settled(who, second, java.util.List.of(
                    CardIdentity.ofPrinting(BOLT, false), CardIdentity.ofPrinting(BOLT, true)));
            if (Owed.waitingFor(who) != 2) {
                helper.fail("Two cards replaced a receipt and the list holds "
                        + Owed.waitingFor(who));
                return;
            }
            helper.succeed();
        } finally {
            Owed.forget(who);
        }
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
