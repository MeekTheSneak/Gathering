package dev.gathering.server;

import dev.gathering.block.PodSignup;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.draft.PodLobby;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.network.CreatePodPayload;
import dev.gathering.network.PodActionPayload;
import dev.gathering.network.PodLobbyPayload;
import dev.gathering.network.Sending;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The signup screen's server half: what each player at a signup is shown, and what the buttons
 * on it do.
 * <p>Every change to a signup is followed by telling everybody it concerns - the seated players
 * and the host - so a pack going in on one side of the table shows on the other without anybody
 * asking. The rules stay in {@link PodSignups} and {@link PodEvents}; this only carries them to
 * and from a screen.
 */
public final class PodLobbies {

    private PodLobbies() {
    }

    /** Creating a signup from the create screen. Only somebody seated here may host. */
    public static void create(ServerPlayer player, CreatePodPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = TableReach.originFor(player, payload.table()).orElse(null);
        if (origin == null) {
            return;
        }
        if (TableSeats.seatOf(level, origin, player.getUUID()).isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.gathering.pod.sit_down_first"));
            return;
        }
        PodSignups.Created created = PodSignups.create(level, origin, player.getUUID(), payload.settings());
        switch (created) {
            case OPEN -> {
                show(player, origin);
                changed(level, origin, player.getUUID());
            }
            case SETTINGS -> player.sendSystemMessage(Component.translatable(
                    payload.settings().problem().orElse("message.gathering.pod.settings")));
            case SOURCE_OFF -> player.sendSystemMessage(Component.translatable("message.gathering.pod.source_off"));
            case BUSY -> player.sendSystemMessage(Component.translatable("message.gathering.pod.busy"));
            case NO_TABLE -> { }
        }
    }

    public static void act(ServerPlayer player, PodActionPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = TableReach.originFor(player, payload.table()).orElse(null);
        if (origin == null || signupAt(level, origin).isEmpty()) {
            return;
        }
        switch (payload.action()) {
            case VIEW -> show(player, origin);
            case PUT_IN -> {
                putInFromInventory(player, origin);
                changed(level, origin, null);
            }
            case WITHDRAW -> {
                int back = PodSignups.withdraw(level, origin, player.getUUID());
                if (back > 0) {
                    player.sendSystemMessage(Component.translatable("message.gathering.pod.packs_back", back));
                }
                changed(level, origin, null);
            }
            case START -> {
                if (PodEvents.start(player, origin)) {
                    changed(level, origin, null);
                }
            }
            case CANCEL -> {
                List<UUID> concerned = concerned(level, origin);
                if (PodSignups.cancel(level, origin, player)) {
                    closed(level, origin, concerned);
                }
            }
        }
    }

    /**
     * Puts in every pack this player is carrying that the event will take, stack by stack.
     * <p>The screen's button for what walking up with a stack in hand does, for a player whose
     * packs are spread across their inventory.
     */
    static int putInFromInventory(ServerPlayer player, BlockPos origin) {
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.has(dev.gathering.registry.GatheringComponents.PACK.get())) {
                total += PodSignups.putIn(player, origin, stack, true);
            }
        }
        List<UUID> seated = PodSignups.seatedAt(player.serverLevel(), origin);
        int owed = signupAt(player.serverLevel(), origin)
                .map(signup -> signup.lobby().stillOwedBy(player.getUUID(), seated)).orElse(0);
        player.sendSystemMessage(total > 0
                ? Component.translatable("message.gathering.pod.packs_in", total, owed)
                : Component.translatable("message.gathering.pod.no_packs_to_put_in", owed));
        return total;
    }

    /** Opens the signup screen for this player. */
    public static void show(ServerPlayer player, BlockPos origin) {
        viewFor(player, origin, true).ifPresent(view -> Sending.to(player, view));
    }

    /**
     * Tells everybody a signup concerns what it looks like now.
     *
     * @param opening somebody who has just done something that should open the screen for
     *                them, or null
     */
    public static void changed(ServerLevel level, BlockPos origin, UUID opening) {
        List<UUID> concerned = concerned(level, origin);
        if (signupAt(level, origin).isEmpty()) {
            closed(level, origin, concerned);
            return;
        }
        for (UUID who : concerned) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(who);
            if (player != null) {
                viewFor(player, origin, who.equals(opening)).ifPresent(view -> Sending.to(player, view));
            }
        }
    }

    /** Tells these players the signup is over, so a screen showing it closes. */
    public static void closed(ServerLevel level, BlockPos origin, List<UUID> concerned) {
        for (UUID who : concerned) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(who);
            if (player != null) {
                Sending.to(player, new PodLobbyPayload(origin, false, false, "", false,
                        dev.gathering.core.draft.PodSettings.usual(dev.gathering.core.draft.PodSettings.Kind.DRAFT),
                        List.of(), "", false));
            }
        }
    }

    /** The seated players and the host: everybody a signup is about. */
    static List<UUID> concerned(ServerLevel level, BlockPos origin) {
        Set<UUID> who = new LinkedHashSet<>(PodSignups.seatedAt(level, origin));
        signupAt(level, origin).ifPresent(signup -> {
            who.add(signup.host());
            signup.held().forEach(held -> who.add(held.contributor()));
        });
        return List.copyOf(who);
    }

    private static Optional<PodLobbyPayload> viewFor(ServerPlayer player, BlockPos origin, boolean show) {
        ServerLevel level = player.serverLevel();
        TableBlockEntity table = anchorTable(level, origin).orElse(null);
        PodSignup signup = table == null ? null : table.signup().orElse(null);
        if (signup == null) {
            return Optional.empty();
        }
        PodLobby lobby = signup.lobby();
        List<PlayerRef> seated = dev.gathering.block.DraftPods.drafters(level, origin);
        List<UUID> ids = seated.stream().map(PlayerRef::id).toList();
        List<PodLobbyPayload.Player> rows = new ArrayList<>();
        for (PlayerRef ref : seated) {
            rows.add(new PodLobbyPayload.Player(ref.name(), lobby.entriesOf(ref.id()).size(),
                    lobby.stillOwedBy(ref.id(), ids)));
        }
        ServerPlayer host = level.getServer().getPlayerList().getPlayer(signup.host());
        String hostName = host != null ? host.getGameProfile().getName()
                : seated.stream().filter(ref -> ref.id().equals(signup.host())).map(PlayerRef::name)
                        .findFirst().orElse("?");
        String status = lobby.notReady(ids).orElse("message.gathering.pod.ready");
        return Optional.of(new PodLobbyPayload(origin, true, show, hostName,
                signup.host().equals(player.getUUID()), signup.settings(), rows, status, table.isOpening()));
    }

    private static Optional<PodSignup> signupAt(ServerLevel level, BlockPos origin) {
        return anchorTable(level, origin).flatMap(TableBlockEntity::signup);
    }

    private static Optional<TableBlockEntity> anchorTable(ServerLevel level, BlockPos origin) {
        return TableSessions.anchorOf(level, origin).flatMap(anchor -> TableBlock.entityAt(level, anchor));
    }
}
