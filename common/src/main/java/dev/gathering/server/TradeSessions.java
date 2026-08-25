package dev.gathering.server;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.collection.CardTally;
import dev.gathering.core.trade.TradeSettlement;
import dev.gathering.core.trade.TradeTable;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.network.Sending;
import dev.gathering.network.TradeActionPayload;
import dev.gathering.network.TradeViewPayload;
import dev.gathering.service.ServerSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The trades happening on this server.
 *
 * <p>What the rules are is {@link TradeTable}'s and what a swap costs is
 * {@link TradeSettlement}'s. This is the part that knows who is online, who is standing where,
 * and whose inventory a card is actually in - none of which the pure layer can be told without
 * becoming the server.
 *
 * <p>One trade per person. Not a limitation to work around: two open tables is how somebody
 * puts the same card up twice and takes two things for it, and a rule that allowed it would
 * need the cards held in escrow to be safe. One at a time is the cheaper answer and the
 * clearer one.
 *
 * <p>Everything is checked again on the way out. Standing next to each other, still online,
 * still holding what was put up: all of it re-asked at the moment of the swap, because a
 * trade table is a conversation and none of those stay true just because they were.
 */
public final class TradeSessions {

    /** How far apart two people may be and still be trading. Arm's length, near enough. */
    public static final double WITHIN = 8.0;

    /** Who is trading with whom. Both people point at the same table. */
    private static final Map<UUID, TradeTable> TABLES = new ConcurrentHashMap<>();

    private TradeSessions() {
    }

    /**
     * Whether this server trades at all.
     *
     * <p>Collection mode's, and only there. With cards conjured out of a decklist, trading one
     * is two people agreeing to swap things they could each have typed - the gesture would
     * work and mean nothing.
     */
    public static boolean isOffered() {
        return ServerSettings.get().modes().collectionEnabled();
    }

    /** Between servers, so one world's half-finished trade is not the next one's. */
    public static void clear() {
        TABLES.clear();
    }

    /** The table this person is at, or null. */
    public static TradeTable at(UUID who) {
        return who == null ? null : TABLES.get(who);
    }

    /**
     * Opens a trade between two people, if neither is already in one.
     *
     * <p>Refused rather than queued when somebody is busy: being pulled into a second trade
     * while reading the first is how a mis-click becomes a mistake.
     */
    public static boolean open(ServerPlayer asking, ServerPlayer other) {
        if (asking == null || other == null || asking == other) {
            return false;
        }
        if (!near(asking, other)) {
            return false;
        }
        // Anybody who logged out mid-trade, forgotten here rather than through a disconnect
        // hook in each loader. Without it their own table outlives them and they come back to
        // a server that says they are already trading, for ever.
        TABLES.keySet().removeIf(who -> asking.server.getPlayerList().getPlayer(who) == null);
        if (TABLES.containsKey(asking.getUUID()) || TABLES.containsKey(other.getUUID())) {
            say(asking, "message.gathering.trade_busy");
            return false;
        }
        TradeTable table = TradeTable.between(asking.getUUID(), other.getUUID());
        TABLES.put(asking.getUUID(), table);
        TABLES.put(other.getUUID(), table);
        other.sendSystemMessage(Component.translatable(
                "message.gathering.trade_opened", asking.getGameProfile().getName()));
        show(asking, table);
        show(other, table);
        return true;
    }

    /** One thing somebody did at their table. */
    public static void handle(ServerPlayer player, TradeActionPayload asked) {
        if (player == null || asked == null) {
            return;
        }
        TradeTable table = TABLES.get(player.getUUID());
        if (table == null) {
            return;
        }
        ServerPlayer other = across(player, table);
        if (other == null || !near(player, other)) {
            // Walked off, logged out, or stepped away. Whoever is left is told, and the table
            // goes rather than sitting there waiting for somebody who is not coming back.
            end(table, player, other, "message.gathering.trade_gone");
            return;
        }
        if (asked.action() == TradeActionPayload.Action.CLOSE) {
            end(table, player, other, "message.gathering.trade_closed");
            return;
        }

        TradeTable next = switch (asked.action()) {
            case PUT -> put(player, table, asked);
            case CLEAR -> table.clearOffer(player.getUUID());
            case AGREE -> table.agree(player.getUUID());
            case THINK_AGAIN -> table.thinkAgain(player.getUUID());
            case CLOSE -> table;
        };
        if (next.isStruck()) {
            settle(next, player, other);
            return;
        }
        remember(next);
        show(player, next);
        show(other, next);
    }

    /** Ends every trade this person is in, for a disconnect or a server stop. */
    public static void leave(ServerPlayer player) {
        if (player == null) {
            return;
        }
        TradeTable table = TABLES.remove(player.getUUID());
        if (table == null) {
            return;
        }
        table.across(player.getUUID()).ifPresent(TABLES::remove);
        ServerPlayer other = across(player, table);
        if (other != null) {
            say(other, "message.gathering.trade_gone");
            Sending.to(other, TradeViewPayload.over(player.getGameProfile().getName()));
        }
    }

    // ------------------------------------------------------------------ bits

    /**
     * Puts cards up, never more than the person actually has.
     *
     * <p>Clamped here as well as checked at the swap. An offer of four cards somebody owns one
     * of would sit on the other person's screen looking like four cards, and fail at the last
     * moment for a reason neither of them could see.
     */
    private static TradeTable put(ServerPlayer player, TradeTable table,
            TradeActionPayload asked) {
        CardComponent card = asked.card().orElse(null);
        if (card == null) {
            return table;
        }
        CardIdentity wanted = card.faceUp().toIdentity();
        int has = counting(player).of(wanted);
        return table.putUp(player.getUUID(), wanted, Math.min(asked.howMany(), has));
    }

    /**
     * The swap.
     *
     * <p>Both inventories are counted again here rather than trusted from when the cards were
     * put up, because a card can be put up and then dropped, spent or lost in lava while the
     * other side is still reading.
     */
    private static void settle(TradeTable table, ServerPlayer player, ServerPlayer other) {
        ServerPlayer left = table.left().equals(player.getUUID()) ? player : other;
        ServerPlayer right = left == player ? other : player;

        var settled = TradeSettlement.of(table, counting(left), counting(right)).orElse(null);
        if (settled == null) {
            // Somebody no longer has what they put up. All of it or none of it: handing over
            // the half they could still cover would make losing a card a way to take one.
            end(table, player, other, "message.gathering.trade_short");
            return;
        }

        // Out of both inventories before anything goes into either, so a trade that cannot be
        // completed cannot have half happened.
        List<ItemStack> forLeft = takeFrom(right, table.fromRight());
        List<ItemStack> forRight = takeFrom(left, table.fromLeft());
        give(left, forLeft);
        give(right, forRight);

        TABLES.remove(table.left());
        TABLES.remove(table.right());
        say(left, "message.gathering.trade_done");
        say(right, "message.gathering.trade_done");
        Sending.to(left, TradeViewPayload.over(right.getGameProfile().getName()));
        Sending.to(right, TradeViewPayload.over(left.getGameProfile().getName()));
    }

    /** Every card item this person is carrying, counted by what it is. */
    private static CardTally counting(ServerPlayer player) {
        CardTally.Builder tally = CardTally.builder();
        for (ItemStack stack : carrying(player)) {
            CardItem.cardOf(stack)
                    .map(card -> card.faceUp().toIdentity())
                    .ifPresent(card -> tally.add(card, stack.getCount()));
        }
        return tally.build();
    }

    /** Takes these cards out of an inventory, as the items they are. */
    private static List<ItemStack> takeFrom(ServerPlayer player, CardTally wanted) {
        Map<CardIdentity, Integer> left = new LinkedHashMap<>();
        for (CardIdentity card : wanted.cards()) {
            left.put(card, wanted.of(card));
        }
        List<ItemStack> taken = new ArrayList<>();
        for (ItemStack stack : carrying(player)) {
            CardIdentity card = CardItem.cardOf(stack)
                    .map(held -> held.faceUp().toIdentity()).orElse(null);
            int still = card == null ? 0 : left.getOrDefault(card, 0);
            if (still <= 0) {
                continue;
            }
            int take = Math.min(still, stack.getCount());
            ItemStack moved = stack.copyWithCount(take);
            stack.shrink(take);
            left.put(card, still - take);
            taken.add(moved);
        }
        return taken;
    }

    /**
     * Every stack this person is carrying that a card could be in.
     *
     * <p>The main inventory and the off hand. A card held in the off hand is a card, and a
     * trade that could not see it would be one where the card you are holding out to somebody
     * is the one card you cannot put up.
     */
    private static List<ItemStack> carrying(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().offhand);
        return stacks;
    }

    private static void give(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private static void end(TradeTable table, ServerPlayer player, ServerPlayer other,
            String why) {
        TABLES.remove(table.left());
        TABLES.remove(table.right());
        if (player != null) {
            say(player, why);
            Sending.to(player, TradeViewPayload.over(
                    other == null ? "" : other.getGameProfile().getName()));
        }
        if (other != null) {
            say(other, why);
            Sending.to(other, TradeViewPayload.over(
                    player == null ? "" : player.getGameProfile().getName()));
        }
    }

    private static void remember(TradeTable table) {
        TABLES.put(table.left(), table);
        TABLES.put(table.right(), table);
    }

    /** The other person, if they are still online and in this world. */
    private static ServerPlayer across(ServerPlayer player, TradeTable table) {
        UUID other = table.across(player.getUUID()).orElse(null);
        return other == null ? null : player.server.getPlayerList().getPlayer(other);
    }

    private static boolean near(ServerPlayer one, ServerPlayer two) {
        return one.level() == two.level() && one.distanceToSqr(two) <= WITHIN * WITHIN;
    }

    /** The table as one person sees it, with the pictures for what they are being offered. */
    private static void show(ServerPlayer player, TradeTable table) {
        ServerPlayer other = across(player, table);
        String name = other == null ? "" : other.getGameProfile().getName();
        UUID them = table.across(player.getUUID()).orElse(null);

        // What is being offered to somebody is a card they are being shown on purpose, so its
        // name and picture go with it - without them the other half of the table is a row of
        // sleeves under a count, which is not something anybody can agree to.
        //
        // Through the same channel the table uses, which remembers what each client has
        // already been told. A trade redraws on every click by either person, and re-sending
        // the same printings on all of them would be a lookup per click aimed at somebody
        // else's Scryfall quota.
        CardArtPush.send(player, printingsIn(table.offerFrom(them)));

        Sending.to(player, new TradeViewPayload(
                name,
                piles(table.offerFrom(player.getUUID())),
                piles(table.offerFrom(them)),
                table.hasAgreed(player.getUUID()),
                them != null && table.hasAgreed(them),
                table.stage() == TradeTable.Stage.CLOSED));
    }

    /** The printings in an offer, for telling somebody what they are being shown. */
    private static java.util.Set<UUID> printingsIn(CardTally tally) {
        java.util.Set<UUID> printings = new java.util.LinkedHashSet<>();
        for (CardIdentity card : tally.cards()) {
            card.printing().ifPresent(printings::add);
        }
        return printings;
    }

    private static List<TradeViewPayload.Pile> piles(CardTally tally) {
        List<TradeViewPayload.Pile> piles = new ArrayList<>(tally.distinct());
        for (CardIdentity card : tally.cards()) {
            piles.add(new TradeViewPayload.Pile(CardComponent.of(card), tally.of(card)));
        }
        return piles;
    }

    private static void say(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.translatable(message));
    }
}
