package dev.gathering.server;

import dev.gathering.block.DraftPods;
import dev.gathering.block.PodSignup;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSessions;
import dev.gathering.core.draft.PodLobby;
import dev.gathering.core.draft.PodSettings;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.item.PackComponent;
import dev.gathering.registry.GatheringComponents;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Signing up for a draft or sealed event at a table: creating one, putting packs in, taking
 * them back out, and handing every pack back when an event does not happen.
 * <p>Every pack the table takes is somebody's, and every path out of a signup ends with each
 * one back with that person - in their inventory, owed to them on their next join if they are
 * away, or on the table if even that cannot be written down. The rules about which packs count
 * are {@link PodLobby}'s; this is the part with items and players in it.
 */
public final class PodSignups {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private PodSignups() {
    }

    /** How an attempt to create a signup turned out. */
    public enum Created {
        OPEN, SETTINGS, SOURCE_OFF, BUSY, NO_TABLE
    }

    /**
     * Opens a signup at this cluster, hosted by this player.
     *
     * @return what happened; the caller tells the player
     */
    public static Created create(ServerLevel level, BlockPos tableOrigin, UUID host, PodSettings settings) {
        TableBlockEntity table = anchorTable(level, tableOrigin).orElse(null);
        if (table == null) {
            return Created.NO_TABLE;
        }
        if (settings == null || settings.problem().isPresent()) {
            return Created.SETTINGS;
        }
        if (!sourceIsAllowed(settings.source())) {
            return Created.SOURCE_OFF;
        }
        // One thing at a time on a cluster, as with a pod or a game.
        if (table.hasSignup() || table.hasPod() || table.hasSession()) {
            return Created.BUSY;
        }
        table.setSignup(PodSignup.open(host, settings));
        return Created.OPEN;
    }

    /**
     * Whether this server lets an event take its packs from here.
     * <p>Real packs are cards being property, which is collection mode's; packs the server
     * makes up out of nothing are only for a server that allows importing, where cards are not
     * property at all.
     */
    public static boolean sourceIsAllowed(PodSettings.Source source) {
        var modes = dev.gathering.service.ServerSettings.get().modes();
        return source == PodSettings.Source.GENERATED ? modes.importEnabled() : modes.collectionEnabled();
    }

    /**
     * Puts packs from this stack into the signup, as many as this player still owes and the
     * event will take.
     * <p>One click for all of them, because a player holding a stack of three boosters walked
     * up to put in three boosters. A pack refused stays in the hand, and the player is told
     * why.
     *
     * @return how many went in
     */
    public static int putIn(ServerPlayer player, BlockPos tableOrigin, ItemStack stack) {
        int in = putIn(player, tableOrigin, stack, false);
        PodLobbies.changed(player.serverLevel(), tableOrigin, null);
        return in;
    }

    /**
     * The same, saying nothing when {@code quietly}: for putting in several stacks at once,
     * which says what happened once, at the end.
     */
    static int putIn(ServerPlayer player, BlockPos tableOrigin, ItemStack stack, boolean quietly) {
        ServerLevel level = player.serverLevel();
        TableBlockEntity table = anchorTable(level, tableOrigin).orElse(null);
        PodSignup signup = table == null ? null : table.signup().orElse(null);
        if (signup == null) {
            return 0;
        }
        if (table.isOpening()) {
            if (!quietly) {
                player.sendSystemMessage(Component.translatable("message.gathering.pod.opening"));
            }
            return 0;
        }
        PackComponent about = stack.get(GatheringComponents.PACK.get());
        if (about == null) {
            return 0;
        }
        List<UUID> seated = seatedAt(level, tableOrigin);
        PodLobby.PackRef pack = new PodLobby.PackRef(about.setCode(), about.kind(), about.color());
        int putIn = 0;
        String refused = null;
        while (!stack.isEmpty()) {
            PodLobby lobby = signup.lobby();
            refused = lobby.refusal(player.getUUID(), pack, seated).orElse(null);
            if (refused != null) {
                break;
            }
            // Taken from the hand once it is written into the signup, and taken from everybody,
            // creative or not, exactly as a deck put down on a table is. A creative player who
            // kept theirs would be handed a second pack back for every one they put in.
            signup = signup.with(new PodSignup.Held(player.getUUID(), stack));
            stack.shrink(1);
            putIn++;
        }
        if (putIn > 0) {
            table.setSignup(signup);
        }
        if (quietly) {
            return putIn;
        }
        if (putIn > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.pod.packs_in", putIn,
                    signup.lobby().stillOwedBy(player.getUUID(), seated)));
        } else if (refused != null) {
            player.sendSystemMessage(Component.translatable(refused));
        }
        return putIn;
    }

    /** Hands this player's packs back and takes them out of the signup. */
    public static int withdraw(ServerLevel level, BlockPos tableOrigin, UUID player) {
        TableBlockEntity table = anchorTable(level, tableOrigin).orElse(null);
        PodSignup signup = table == null ? null : table.signup().orElse(null);
        if (signup == null || table.isOpening()) {
            return 0;
        }
        List<PodSignup.Held> theirs = signup.heldFor(player);
        if (theirs.isEmpty()) {
            return 0;
        }
        // Out of the signup first, then handed over: the other order leaves a moment in which
        // the pack is both in a hand and still held for the same person.
        table.setSignup(signup.without(player));
        for (PodSignup.Held pack : theirs) {
            handBack(level, tableOrigin, pack);
        }
        return theirs.size();
    }

    /**
     * Ends a signup without an event, handing every pack back.
     * <p>Only the host may call it off. Anybody else walks away with their own packs through
     * {@link #withdraw}, and one person leaving does not end it for everybody else.
     *
     * @return whether it was called off
     */
    public static boolean cancel(ServerLevel level, BlockPos tableOrigin, ServerPlayer asking) {
        TableBlockEntity table = anchorTable(level, tableOrigin).orElse(null);
        PodSignup signup = table == null ? null : table.signup().orElse(null);
        if (signup == null) {
            return false;
        }
        if (table.isOpening()) {
            asking.sendSystemMessage(Component.translatable("message.gathering.pod.opening"));
            return false;
        }
        boolean operator = asking.hasPermissions(2);
        if (!signup.host().equals(asking.getUUID()) && !operator) {
            asking.sendSystemMessage(Component.translatable("message.gathering.pod.only_the_host"));
            return false;
        }
        handBackEverything(level, tableOrigin, table, "pod_signup_cancelled");
        return true;
    }

    /**
     * Closes the signup on this table and hands every pack back to whoever put it in.
     * <p>Through the block entity in hand rather than looked up again, because the table being
     * taken apart is one of the reasons to call this, and by then the world may not answer.
     *
     * @param why the last part of the message key the players are told, or null to say nothing
     */
    public static void handBackEverything(
            ServerLevel level, BlockPos tableOrigin, TableBlockEntity table, String why) {
        UUID host = table.signup().map(PodSignup::host).orElse(null);
        List<UUID> concerned = table.signup().isPresent() ? PodLobbies.concerned(level, tableOrigin) : List.of();
        List<PodSignup.Held> held = table.closeSignup();
        PodLobbies.closed(level, tableOrigin, concerned);
        for (PodSignup.Held pack : held) {
            handBack(level, tableOrigin, pack);
        }
        if (why == null) {
            return;
        }
        java.util.Set<UUID> told = new java.util.LinkedHashSet<>();
        if (host != null) {
            told.add(host);
        }
        for (PodSignup.Held pack : held) {
            told.add(pack.contributor());
        }
        for (UUID who : told) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(who);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.gathering." + why));
            }
        }
    }

    /**
     * Somebody stood up. Their packs go back to them, unless they are sponsoring the event.
     * <p>A player's own packs are held only for the event they are sitting in; standing up is
     * the gesture for leaving it. A sponsor's packs are held for everybody, and a host who
     * stands to fetch a drink has not called the event off - that is what Cancel is for.
     */
    public static void seatReleased(ServerLevel level, BlockPos tableOrigin, UUID player) {
        TableBlockEntity table = anchorTable(level, tableOrigin).orElse(null);
        PodSignup signup = table == null ? null : table.signup().orElse(null);
        if (signup == null) {
            return;
        }
        if (signup.settings().source() == PodSettings.Source.SPONSORED && signup.host().equals(player)) {
            return;
        }
        int back = withdraw(level, tableOrigin, player);
        ServerPlayer leaving = level.getServer().getPlayerList().getPlayer(player);
        if (back > 0 && leaving != null) {
            leaving.sendSystemMessage(Component.translatable("message.gathering.pod.packs_back", back));
        }
        PodLobbies.changed(level, tableOrigin, null);
    }

    /**
     * One pack, back to whoever put it in.
     * <p>In their hands if they are here. Owed to them on their next join if they are not - the
     * same record an interrupted booster uses. On the table only if even that could not be
     * written down, because a pack lying where the event was is still a pack somebody can find.
     */
    static void handBack(ServerLevel level, BlockPos tableOrigin, PodSignup.Held pack) {
        ItemStack stack = pack.pack().copy();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(pack.contributor());
        if (owner != null) {
            Handing.give(owner, stack);
            return;
        }
        PodLobby.PackRef ref = pack.ref();
        if (!ref.setCode().isBlank() && Owed.aPack(pack.contributor(), ref.setCode(), ref.kind(), ref.color())) {
            return;
        }
        LOGGER.warn("Could not record a pack owed to {}; leaving it on the table at {}",
                pack.contributor(), tableOrigin);
        Containers.dropItemStack(level,
                tableOrigin.getX() + 0.5, tableOrigin.getY() + 1.0, tableOrigin.getZ() + 0.5, stack);
    }

    /** Everybody sitting at this cluster, in seat order - which is who an event is for. */
    public static List<UUID> seatedAt(ServerLevel level, BlockPos tableOrigin) {
        return DraftPods.drafters(level, tableOrigin).stream().map(PlayerRef::id).toList();
    }

    private static Optional<TableBlockEntity> anchorTable(ServerLevel level, BlockPos tableOrigin) {
        return TableSessions.anchorOf(level, tableOrigin).flatMap(anchor -> TableBlock.entityAt(level, anchor));
    }
}
