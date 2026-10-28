package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.story.CardStory;
import dev.gathering.core.story.HowItCame;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.PackItem;
import dev.gathering.server.CardGrant;
import dev.gathering.server.CardStories;
import dev.gathering.server.Handing;
import dev.gathering.server.Owed;
import dev.gathering.server.PackWrappers;
import dev.gathering.server.PlayerGone;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What is handed to a player who is lying dead, or who has respawned since it was asked for.
 * <p>Until a player clicks Respawn the server still holds the entity they died as, with its bag already
 * emptied onto the ground, and respawning makes a new one without copying that bag across. Everything
 * the mod handed over looked the player up and put the item in whatever entity it found: a booster whose
 * reveal the death screen closed - which tears it - went into the dead bag and out of the world, and so
 * did a prize, a pool, a deck or a pot paid out to somebody on the death screen. An entity a respawn has
 * already replaced was the same, for work that finished after it.
 * <p>Each of these respawns for real, through the server's own {@code PlayerList#respawn}, so the
 * loader's respawn hook is what hands things over at the end.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RespawnGameTest {

    private static final UUID BOLT = UUID.fromString("dddddddd-4444-4444-8444-444444444444");
    private static final UUID SHOCK = UUID.fromString("eeeeeeee-5555-4555-8555-555555555555");

    /**
     * Killed with a pack's reveal open: the death screen takes its place, and closing the reveal tears the
     * wrapper. The cards are kept for the respawn rather than put in a bag the respawn throws away - and
     * the one the pack was opened for still remembers being pulled.
     */
    @GameTest(template = "empty")
    public static void aWrapperTornFromTheDeathScreenComesWithTheRespawn(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Owed.forget(player.getUUID());
        ServerPlayer back = null;
        try {
            String token = PackWrappers.hold(player, null, "tst", CardIdentity.ofPrinting(BOLT, false), cards(),
                    List.of());
            if (token == null) {
                helper.fail("the wrapper could not be written down");
                return;
            }
            onTheDeathScreen(player);
            PackWrappers.torn(player, token);
            if (cardsIn(player) != 0) {
                helper.fail("a wrapper torn from the death screen put " + cardsIn(player)
                        + " card(s) in a bag that respawning throws away");
                return;
            }
            if (Owed.waitingFor(player.getUUID()) != 2) {
                helper.fail("a wrapper torn from the death screen left " + Owed.waitingFor(player.getUUID())
                        + " card(s) owed, not 2");
                return;
            }
            back = respawn(helper, player);
            if (cardsIn(back) != 2) {
                helper.fail("respawning handed over " + cardsIn(back) + " of the pack's 2 cards");
                return;
            }
            if (Owed.waitingFor(back.getUUID()) != 0) {
                helper.fail("the cards were handed over on respawning and are still owed as well");
                return;
            }
            if (!pulled(back, BOLT)) {
                helper.fail("the card the pack was opened for came back from the death screen without its story");
                return;
            }
            helper.succeed();
        } finally {
            Owed.forget(player.getUUID());
            TestPlayers.leaving(helper, back);
        }
    }

    /**
     * Nothing goes into the bag of a player on the death screen - not what they are owed when they join
     * still lying there, and not anything handed to them meanwhile, which is what a prize, a draft pool, a
     * deck back from a table or a pot paid out does for somebody who died just then. All of it comes with
     * the respawn.
     */
    @GameTest(template = "empty")
    public static void nothingIsPutInTheBagOfSomebodyOnTheDeathScreen(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Owed.forget(player.getUUID());
        ServerPlayer back = null;
        try {
            if (!Owed.aPack(player.getUUID(), "DMU", "draft", "")) {
                helper.fail("the fixture's owed pack could not be written down");
                return;
            }
            onTheDeathScreen(player);
            // Joining while still dead - a player who quit from the death screen comes back to it.
            PlayerGone.arrived(player);
            // And something handed over while they lie there.
            ItemStack card = CardItem.of(CardComponent.of(CardIdentity.ofPrinting(SHOCK, false)));
            Handing.give(player, card);
            if (!card.isEmpty()) {
                helper.fail("a card handed to somebody on the death screen was neither given nor kept");
                return;
            }
            if (itemsIn(player) != 0) {
                helper.fail(itemsIn(player) + " thing(s) were put in the bag of a player on the death screen,"
                        + " which respawning throws away");
                return;
            }
            if (Owed.waitingFor(player.getUUID()) != 2) {
                helper.fail("a player on the death screen is owed " + Owed.waitingFor(player.getUUID())
                        + " thing(s), not the pack and the card");
                return;
            }
            back = respawn(helper, player);
            if (countOf(back, PackItem.class) != 1 || cardsIn(back) != 1) {
                helper.fail("respawning handed over " + countOf(back, PackItem.class) + " pack(s) and "
                        + cardsIn(back) + " card(s), not one of each");
                return;
            }
            if (Owed.waitingFor(back.getUUID()) != 0) {
                helper.fail("what respawning handed over is still owed as well");
                return;
            }
            helper.succeed();
        } finally {
            Owed.forget(player.getUUID());
            TestPlayers.leaving(helper, back);
        }
    }

    /**
     * A respawn does not hand over the cards of a wrapper that is still waiting to be torn. A client that
     * respawns at once (the immediate-respawn rule) closes the reveal, and so tears it, only after it hears
     * it has respawned - so the respawn reaches the server first. Paying those cards out then would hand
     * them over bare, and leave the tear that follows with nothing to give.
     */
    @GameTest(template = "empty")
    public static void aRespawnLeavesAWrapperToItsTear(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Owed.forget(player.getUUID());
        ServerPlayer back = null;
        try {
            String token = PackWrappers.hold(player, null, "tst", CardIdentity.ofPrinting(BOLT, false), cards(),
                    List.of());
            if (token == null) {
                helper.fail("the wrapper could not be written down");
                return;
            }
            back = respawn(helper, player);
            if (cardsIn(back) != 0) {
                helper.fail("respawning handed over " + cardsIn(back) + " card(s) of a wrapper still waiting to be torn");
                return;
            }
            PackWrappers.torn(back, token);
            if (cardsIn(back) != 2) {
                helper.fail("tearing the wrapper after respawning handed over " + cardsIn(back) + " of 2 cards");
                return;
            }
            if (!pulled(back, BOLT)) {
                helper.fail("the card the pack was opened for lost its story to the respawn");
                return;
            }
            helper.succeed();
        } finally {
            Owed.forget(player.getUUID());
            TestPlayers.leaving(helper, back);
        }
    }

    /**
     * Work that finishes after its player respawned hands the result to the player they are now, not to
     * the entity the respawn replaced - which still holds their connection, so they were told it arrived.
     * A card looked up by a command is the case here; an imported deck and a granted pack finish the same way.
     */
    @GameTest(template = "empty")
    public static void whatFinishesAfterARespawnReachesThePlayerTheyAreNow(GameTestHelper helper) {
        ServerPlayer asked = helper.makeMockServerPlayerInLevel();
        Owed.forget(asked.getUUID());
        ServerPlayer back = null;
        try {
            // The lookup is out; the player dies and respawns before it comes back.
            onTheDeathScreen(asked);
            back = respawn(helper, asked);
            CardGrant.give(asked, metadata(SHOCK), false);
            if (cardsIn(asked) != 0) {
                helper.fail("a card that arrived after a respawn went to the entity the respawn replaced");
                return;
            }
            if (cardsIn(back) != 1) {
                helper.fail("a card that arrived after a respawn reached the player as " + cardsIn(back) + " card(s)");
                return;
            }
            helper.succeed();
        } finally {
            Owed.forget(asked.getUUID());
            TestPlayers.leaving(helper, back);
        }
    }

    // ------------------------------------------------------------------ bits

    /**
     * Dead and not yet respawned: what the server holds for a player looking at the death screen. Their
     * health only - the dying itself empties the bag onto the ground, and this one is empty already.
     */
    private static void onTheDeathScreen(ServerPlayer player) {
        player.setHealth(0.0F);
    }

    /** What clicking Respawn does, including the loader's respawn hook. */
    private static ServerPlayer respawn(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
    }

    private static List<CardIdentity> cards() {
        return List.of(CardIdentity.ofPrinting(BOLT, false), CardIdentity.ofPrinting(SHOCK, true));
    }

    /** Whether this player holds the card of this printing with a story that begins with being pulled. */
    private static boolean pulled(ServerPlayer player, UUID printing) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            boolean thatCard = CardItem.cardOf(stack)
                    .flatMap(card -> card.faceUp().toIdentity().printing())
                    .filter(printing::equals)
                    .isPresent();
            if (thatCard) {
                CardStory story = CardStories.storyOf(stack);
                return !story.isEmpty() && story.chapters().stream().anyMatch(chapter -> chapter.how() == HowItCame.PULLED);
            }
        }
        return false;
    }

    private static int cardsIn(ServerPlayer player) {
        return countOf(player, CardItem.class);
    }

    /** Everything in every slot, armor and off hand included. */
    private static int itemsIn(ServerPlayer player) {
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            found += player.getInventory().getItem(slot).getCount();
        }
        return found;
    }

    private static int countOf(ServerPlayer player, Class<?> kind) {
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && kind.isInstance(stack.getItem())) {
                found += stack.getCount();
            }
        }
        return found;
    }

    private static CardMetadata metadata(UUID printing) {
        return new CardMetadata(
                printing, printing, "Shock", "{R}", 1.0, "Instant", "Does a thing.",
                Set.of(), Set.of(), List.<CardFace>of(), "normal", "tst", "Test", "1",
                Rarity.COMMON, false, true, true, false, false,
                List.of("paper"), Map.of(), Map.of(), "https://scryfall.com/");
    }
}
