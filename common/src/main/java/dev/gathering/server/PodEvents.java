package dev.gathering.server;

import dev.gathering.block.DraftPods;
import dev.gathering.block.PodSignup;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.draft.DraftPod;
import dev.gathering.core.draft.PodLobby;
import dev.gathering.core.draft.PodRecord;
import dev.gathering.core.draft.PodSettings;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.DraftedPool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turning a signup into an event: opening every pack, then either passing them round a draft
 * or handing each player their sealed pool - and, at the end, giving the cards to whoever the
 * host said.
 * <p>Opening waits on the card pipeline, so it is done in two halves. Nothing is taken out of
 * the signup until every pack has opened: a restart, a failure or a change at the table in the
 * middle leaves the signup exactly as it was, packs and all. Only when everything is back, on
 * the server thread, and the table still looks the way it did when the host pressed start, are
 * the packs used up and the event begun - in one step, so they are never both held and opened.
 */
public final class PodEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private PodEvents() {
    }

    /**
     * The host starts the event at this table.
     *
     * @return whether the opening began; the host is told either way
     */
    public static boolean start(ServerPlayer host, BlockPos tableOrigin) {
        ServerLevel level = host.serverLevel();
        TableBlockEntity table = anchorTable(level, tableOrigin).orElse(null);
        PodSignup signup = table == null ? null : table.signup().orElse(null);
        if (signup == null) {
            return false;
        }
        if (!signup.host().equals(host.getUUID())) {
            host.sendSystemMessage(Component.translatable("message.gathering.pod.only_the_host_starts"));
            return false;
        }
        if (table.isOpening()) {
            host.sendSystemMessage(Component.translatable("message.gathering.pod.opening"));
            return false;
        }
        List<UUID> seated = PodSignups.seatedAt(level, tableOrigin);
        PodLobby lobby = signup.lobby();
        String notReady = lobby.notReady(seated).orElse(null);
        if (notReady != null) {
            host.sendSystemMessage(Component.translatable(notReady));
            return false;
        }
        if (!PodSignups.sourceIsAllowed(signup.settings().source())) {
            host.sendSystemMessage(Component.translatable("message.gathering.pod.source_off"));
            return false;
        }
        PodLobby.Plan plan = lobby.plan(seated).orElseThrow();

        table.setOpening(true);
        host.sendSystemMessage(Component.translatable("message.gathering.pod.opening"));
        List<List<CompletableFuture<List<CardIdentity>>>> draws = new ArrayList<>();
        for (List<PodLobby.Entry> packs : plan.bySeat()) {
            List<CompletableFuture<List<CardIdentity>>> seat = new ArrayList<>();
            for (PodLobby.Entry entry : packs) {
                seat.add(PackOpening.draw(entry.pack().setCode(), entry.pack().kind(), entry.pack().color()));
            }
            draws.add(seat);
        }
        CompletableFuture<?>[] all = draws.stream().flatMap(List::stream).toArray(CompletableFuture[]::new);
        // Back on the server thread of the world that asked, and only that one: a restart
        // while the packs are opening leaves the signup saved as it was, and this result must
        // not land in whatever world is running by the time it arrives.
        CompletableFuture.allOf(all).whenComplete(ServerRun.onServerThread(host, (ignored, failure) -> {
            TableBlockEntity now = anchorTable(level, tableOrigin).orElse(null);
            if (now != null) {
                now.setOpening(false);
                PodLobbies.changed(level, tableOrigin, null);
            }
            if (failure != null) {
                LOGGER.warn("Opening an event's packs at {} failed", tableOrigin, failure);
                tell(level, signup.host(), Component.translatable(
                        "message.gathering.pod.open_failed", Failures.rootMessage(failure)));
                return;
            }
            List<List<List<CardIdentity>>> opened = new ArrayList<>();
            for (List<CompletableFuture<List<CardIdentity>>> seat : draws) {
                List<List<CardIdentity>> packs = new ArrayList<>();
                for (CompletableFuture<List<CardIdentity>> draw : seat) {
                    List<CardIdentity> cards = draw.join();
                    if (cards.isEmpty()) {
                        tell(level, signup.host(), Component.translatable("message.gathering.pod.pack_empty"));
                        return;
                    }
                    packs.add(cards);
                }
                opened.add(packs);
            }
            begin(level, tableOrigin, signup, seated, plan, opened);
        }));
        return true;
    }

    /**
     * Uses the packs up and starts the event, if the table is still as the host left it.
     * <p>Public for the in-world tests, which have no card pipeline to open real packs with
     * and hand in what the packs held instead - everything from here on is the real path.
     *
     * @return whether the event began
     */
    public static boolean begin(
            ServerLevel level, BlockPos tableOrigin, PodSignup expected, List<UUID> seatedThen,
            PodLobby.Plan plan, List<List<List<CardIdentity>>> opened) {
        TableBlockEntity table = anchorTable(level, tableOrigin).orElse(null);
        PodSignup signup = table == null ? null : table.signup().orElse(null);
        if (signup == null || !signup.equals(expected)) {
            // Called off, broken, or a pack went in or came out while the packs were opening.
            // The signup that is there now - if any - is still holding its own packs.
            tell(level, expected.host(), Component.translatable("message.gathering.pod.changed_while_opening"));
            return false;
        }
        List<UUID> seated = PodSignups.seatedAt(level, tableOrigin);
        if (!seated.equals(seatedThen)) {
            // Somebody sat down or stood up. Standing up while the packs were opening could
            // not hand their packs back, so they are handed back now.
            for (UUID contributor : new java.util.LinkedHashSet<>(signup.held().stream()
                    .map(PodSignup.Held::contributor).toList())) {
                if (!seated.contains(contributor)) {
                    PodSignups.seatReleased(level, tableOrigin, contributor);
                }
            }
            tell(level, expected.host(), Component.translatable("message.gathering.pod.changed_while_opening"));
            return false;
        }

        PodSettings settings = signup.settings();
        String podName = tableOrigin.toShortString();
        PodRecord record = PodRecord.of(signup.lobby(), seated, plan, opened, podName);
        // The packs are used up here and nowhere else: the signup closes, and what it was
        // holding is either opened into the record or handed back unused.
        List<UUID> concerned = PodLobbies.concerned(level, tableOrigin);
        List<PodSignup.Held> held = table.closeSignup();
        PodLobbies.closed(level, tableOrigin, concerned);
        handBackUnused(level, tableOrigin, held, plan);

        if (settings.kind() == PodSettings.Kind.SEALED) {
            giveOwed(level, tableOrigin, record, record.owed(record.sealedPools()), true);
            for (UUID player : seated) {
                tell(level, player, Component.translatable("message.gathering.pod.sealed_open"));
            }
            return true;
        }

        List<PlayerRef> drafters = DraftPods.drafters(level, tableOrigin);
        table.setPodRecord(record);
        table.setPod(DraftPod.opening(drafters, record.rounds(),
                settings.cardsGo() == PodSettings.CardsGo.PLAYERS_KEEP, settings.picksPerTurn()));
        DraftBroadcast.sendToPod(level, tableOrigin, true);
        return true;
    }

    /**
     * Hands out what a finished draft owes, for an event that recorded its packs.
     * <p>Players keeping their picks is the ordinary draft ending and is handled where it
     * always was; this is the other two - every card to the sponsor, or back to whoever put
     * each pack in - which that ending has no idea about.
     *
     * @return whether this event's cards were handed out here
     */
    public static boolean handOutFinishedDraft(ServerLevel level, BlockPos tableOrigin, DraftPod pod) {
        TableBlockEntity table = anchorTable(level, tableOrigin).orElse(null);
        PodRecord record = table == null ? null : table.podRecord().orElse(null);
        if (record == null || record.cardsGo() == PodSettings.CardsGo.PLAYERS_KEEP) {
            return false;
        }
        List<List<CardIdentity>> pools = new ArrayList<>();
        for (int seat = 0; seat < pod.drafters().size(); seat++) {
            pools.add(pod.state().poolOf(dev.gathering.core.draft.DrafterId.of(seat)));
        }
        giveOwed(level, tableOrigin, record, record.owed(pools), false);
        return true;
    }

    /**
     * An event's table is going away before its draft could finish. Every contributor gets
     * back exactly what their own packs held, whatever the host chose: nobody finished
     * drafting anything to keep.
     */
    public static void tableGoneMidDraft(ServerLevel level, BlockPos tableOrigin, TableBlockEntity table) {
        PodRecord record = table.podRecord().orElse(null);
        if (record == null) {
            return;
        }
        giveOwed(level, tableOrigin, record, record.backToContributors(), false);
        table.endPod();
    }

    /**
     * Gives each person what they are owed.
     * <p>As a deck to build from when it is a player's own pool - the whole of it in the
     * sideboard, with the pool it came from recorded so the limited check applies, exactly as
     * a cube draft's pool comes out. As a deck of returned cards otherwise. Somebody away is
     * owed the cards on their next join, which keeps every card, if not the deck it came in.
     */
    private static void giveOwed(
            ServerLevel level, BlockPos tableOrigin, PodRecord record,
            Map<UUID, List<CardIdentity>> owed, boolean pools) {
        owed.forEach((who, cards) -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(who);
            boolean aPool = pools && record.cardsGo() == PodSettings.CardsGo.PLAYERS_KEEP;
            if (player == null) {
                if (!Owed.cards(who, cards)) {
                    LOGGER.error("Could not record {} cards owed to {} from the event at {}; they are lost",
                            cards.size(), who, tableOrigin);
                }
                return;
            }
            List<CardComponent> components = new ArrayList<>(cards.size());
            for (CardIdentity card : cards) {
                components.add(CardComponent.of(card));
            }
            String name = Component.translatable(aPool
                    ? "item.gathering.sealed_pool" : "item.gathering.returned_cards").getString();
            ItemStack stack = DeckItem.of(new DeckComponent(name, "", Optional.of(who), List.of(), List.of(), components)
                    .colored(dev.gathering.core.card.DeckColors.pick(level.getRandom().nextLong())));
            if (aPool) {
                stack.set(dev.gathering.registry.GatheringComponents.POOL.get(),
                        new DraftedPool(components, record.podName()));
            }
            Handing.give(player, stack);
            player.sendSystemMessage(Component.translatable(aPool
                    ? "message.gathering.pod.pool_given" : "message.gathering.pod.cards_given", cards.size()));
        });
    }

    /** Packs the plan did not open go back to whoever put them in. */
    private static void handBackUnused(
            ServerLevel level, BlockPos tableOrigin, List<PodSignup.Held> held, PodLobby.Plan plan) {
        // Matched by position: the plan was made from this signup's entries, in order, so the
        // n-th unused entry of a contributor is the n-th of their held packs the plan left.
        List<PodSignup.Held> remaining = new ArrayList<>(held);
        for (List<PodLobby.Entry> packs : plan.bySeat()) {
            for (PodLobby.Entry used : packs) {
                if (used.contributor() == null) {
                    continue;
                }
                for (int index = 0; index < remaining.size(); index++) {
                    PodSignup.Held candidate = remaining.get(index);
                    if (candidate.contributor().equals(used.contributor())
                            && candidate.ref().equals(used.pack())) {
                        remaining.remove(index);
                        break;
                    }
                }
            }
        }
        for (PodSignup.Held pack : remaining) {
            PodSignups.handBack(level, tableOrigin, pack);
        }
    }

    private static void tell(ServerLevel level, UUID who, Component message) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(who);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    private static Optional<TableBlockEntity> anchorTable(ServerLevel level, BlockPos tableOrigin) {
        return TableSessions.anchorOf(level, tableOrigin).flatMap(anchor -> TableBlock.entityAt(level, anchor));
    }
}
