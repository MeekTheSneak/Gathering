package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.TradeActionPayload;
import dev.gathering.server.TradeSessions;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Trading, in a running world.
 *
 * <p>The rules are pure and checked in {@code :core}; what cannot be checked there is the half
 * that moves things. Cards existing in two places at once is the one failure a trade must not
 * have, and it is a failure of this half rather than of the arithmetic.
 *
 * <p>Every test here counts the cards on both sides afterwards. A trade that went through and
 * a trade that fell over should both leave the same number of cards in the world.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TradeGameTest {

    private static final UUID BOLT = UUID.fromString("aaaaaaaa-1111-4111-8111-111111111111");
    private static final UUID RING = UUID.fromString("bbbbbbbb-2222-4222-8222-222222222222");

    /** Cards cross, and neither side keeps what they gave. */
    @GameTest(template = "empty")
    public static void whatEachSidePutsUpCrosses(GameTestHelper helper) {
        ServerPlayer ana = standing(helper);
        ServerPlayer ben = standing(helper);
        give(ana, BOLT, 4);
        give(ben, RING, 1);

        if (!TradeSessions.open(ana, ben)) {
            helper.fail("Two people standing together could not open a trade");
            return;
        }
        TradeSessions.handle(ana, TradeActionPayload.put(card(BOLT), 2));
        TradeSessions.handle(ben, TradeActionPayload.put(card(RING), 1));
        TradeSessions.handle(ana, TradeActionPayload.of(TradeActionPayload.Action.AGREE));
        TradeSessions.handle(ben, TradeActionPayload.of(TradeActionPayload.Action.AGREE));

        if (count(ana, BOLT) != 2 || count(ana, RING) != 1) {
            helper.fail("Ana has " + count(ana, BOLT) + " bolts and " + count(ana, RING)
                    + " rings; two and one were expected");
            return;
        }
        if (count(ben, BOLT) != 2 || count(ben, RING) != 0) {
            helper.fail("Ben has " + count(ben, BOLT) + " bolts and " + count(ben, RING)
                    + " rings; two and none were expected");
            return;
        }
        if (TradeSessions.at(ana.getUUID()) != null) {
            helper.fail("The trade is still open after it went through");
            return;
        }
        done(helper, ana, ben);
    }

    /**
     * Changing an offer takes back both agreements.
     *
     * <p>The scam this whole feature is shaped around: agree, wait for the other side, swap
     * the good card for a worse one, and take theirs.
     */
    @GameTest(template = "empty")
    public static void aChangedOfferUnAgreesEverybody(GameTestHelper helper) {
        ServerPlayer ana = standing(helper);
        ServerPlayer ben = standing(helper);
        give(ana, BOLT, 4);
        give(ben, RING, 2);
        TradeSessions.open(ana, ben);

        TradeSessions.handle(ana, TradeActionPayload.put(card(BOLT), 4));
        TradeSessions.handle(ben, TradeActionPayload.put(card(RING), 2));
        TradeSessions.handle(ben, TradeActionPayload.of(TradeActionPayload.Action.AGREE));
        TradeSessions.handle(ana, TradeActionPayload.put(card(BOLT), 1));
        // If Ben's agreement survived that, this next line takes his rings for one bolt.
        TradeSessions.handle(ana, TradeActionPayload.of(TradeActionPayload.Action.AGREE));

        if (count(ben, RING) != 2 || count(ana, BOLT) != 4) {
            helper.fail("A trade went through on an agreement given to a different table");
            return;
        }
        if (TradeSessions.at(ana.getUUID()) == null) {
            helper.fail("The trade closed rather than waiting for Ben to look again");
            return;
        }
        done(helper, ana, ben);
    }

    /** A card that went missing stops the whole trade, not half of it. */
    @GameTest(template = "empty")
    public static void allOfItOrNoneOfIt(GameTestHelper helper) {
        ServerPlayer ana = standing(helper);
        ServerPlayer ben = standing(helper);
        give(ana, BOLT, 2);
        give(ben, RING, 1);
        TradeSessions.open(ana, ben);

        TradeSessions.handle(ana, TradeActionPayload.put(card(BOLT), 2));
        TradeSessions.handle(ben, TradeActionPayload.put(card(RING), 1));
        TradeSessions.handle(ana, TradeActionPayload.of(TradeActionPayload.Action.AGREE));
        // Ana's bolts leave her inventory between the two agreements.
        ana.getInventory().clearContent();
        TradeSessions.handle(ben, TradeActionPayload.of(TradeActionPayload.Action.AGREE));

        if (count(ben, RING) != 1) {
            helper.fail("Ben's ring left for cards that were no longer there");
            return;
        }
        if (count(ben, BOLT) != 0) {
            helper.fail("Ben was given bolts that did not exist");
            return;
        }
        done(helper, ana, ben);
    }

    /** Nobody can put up more than they have, however the payload was written. */
    @GameTest(template = "empty")
    public static void nobodyPutsUpWhatTheyDoNotHave(GameTestHelper helper) {
        ServerPlayer ana = standing(helper);
        ServerPlayer ben = standing(helper);
        give(ana, BOLT, 1);
        TradeSessions.open(ana, ben);

        TradeSessions.handle(ana, TradeActionPayload.put(card(BOLT), 64));

        var table = TradeSessions.at(ana.getUUID());
        if (table == null
                || table.offerFrom(ana.getUUID()).of(CardIdentity.ofPrinting(BOLT, false)) != 1) {
            helper.fail("An offer of sixty-four was taken from somebody holding one");
            return;
        }
        done(helper, ana, ben);
    }

    /** Standing across the room is not standing across a table. */
    @GameTest(template = "empty")
    public static void tradingNeedsBothOfYouThere(GameTestHelper helper) {
        ServerPlayer ana = standing(helper);
        ServerPlayer ben = standing(helper);
        give(ana, BOLT, 4);
        TradeSessions.open(ana, ben);
        TradeSessions.handle(ana, TradeActionPayload.put(card(BOLT), 1));

        ben.setPos(ben.getX() + 64.0, ben.getY(), ben.getZ());
        TradeSessions.handle(ana, TradeActionPayload.of(TradeActionPayload.Action.AGREE));

        if (TradeSessions.at(ana.getUUID()) != null) {
            helper.fail("A trade carried on with one of them across the room");
            return;
        }
        if (count(ana, BOLT) != 4) {
            helper.fail("Cards moved in a trade that should have ended");
            return;
        }
        done(helper, ana, ben);
    }

    /** One trade at a time, because two is how a card gets sold twice. */
    @GameTest(template = "empty")
    public static void oneTradeAtATime(GameTestHelper helper) {
        ServerPlayer ana = standing(helper);
        ServerPlayer ben = standing(helper);
        ServerPlayer cat = standing(helper);
        TradeSessions.open(ana, ben);

        if (TradeSessions.open(cat, ana)) {
            helper.fail("Ana was pulled into a second trade while reading the first");
            return;
        }
        done(helper, ana, ben, cat);
    }

    // ------------------------------------------------------------------ bits

    private static void done(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            TradeSessions.leave(player);
            player.discard();
        }
        helper.succeed();
    }

    /** Both of them in the same spot, because a trade is two people standing together. */
    private static ServerPlayer standing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(helper.absoluteVec(new net.minecraft.core.BlockPos(1, 1, 1).getCenter()));
        return player;
    }

    private static CardComponent card(UUID printing) {
        return CardComponent.of(CardIdentity.ofPrinting(printing, false));
    }

    private static void give(ServerPlayer player, UUID printing, int howMany) {
        for (int one = 0; one < howMany; one++) {
            player.getInventory().add(CardItem.of(card(printing)));
        }
    }

    private static int count(ServerPlayer player, UUID printing) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(GatheringContent.CARD.get())
                    && CardItem.cardOf(stack)
                            .map(card -> card.scryfallId().map(printing::equals).orElse(false))
                            .orElse(false)) {
                found += stack.getCount();
            }
        }
        return found;
    }
}
