package dev.gathering.block;

import dev.gathering.item.GatheringContent;
import dev.gathering.server.events.EventBoard;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which tournament a Scorekeeper's Desk is the desk of, and the label floating over it.
 * <p>Only the tournament is saved: the event keeps the rest. The label - the tournament's name
 * and where it has got to - is worked out again from the event once a second and sent to clients
 * when it changes, so a desk whose tournament finished, was cancelled or was deleted while its
 * chunk was unloaded says so the moment anybody is near it.
 */
public class ScorekeepersDeskBlockEntity extends BlockEntity {

    public static final String ID = "scorekeepers_desk";
    private static final String EVENT_KEY = "event";
    private static final String LABEL_KEY = "label";

    /**
     * What the label says. An empty name is no label.
     *
     * @param phase  the tournament's phase, lower case, as its lang key names it
     * @param winner the winner's name once it has finished, or blank
     */
    public record Label(String name, String phase, int round, int rounds, String winner) {

        public static final Label NONE = new Label("", "", 0, 0, "");

        public boolean isShown() {
            return !name.isEmpty();
        }

        static Label of(EventBoard.DeskLabel event) {
            if (event.phase() == dev.gathering.core.tournament.Tournament.Phase.CANCELLED) {
                return NONE;
            }
            String phase = event.phase().name().toLowerCase(java.util.Locale.ROOT);
            // "Sign up here" only where signing up happens: a desk the tournament's sign-up has moved
            // away from says the tournament is signing up, and not that it is here.
            if (event.phase() == dev.gathering.core.tournament.Tournament.Phase.SIGNUP && !event.signsUpHere()) {
                phase = "signup_elsewhere";
            }
            return new Label(event.name(), phase, event.round(), event.rounds(), event.winner());
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putString("phase", phase);
            tag.putInt("round", round);
            tag.putInt("rounds", rounds);
            tag.putString("winner", winner);
            return tag;
        }

        static Label load(CompoundTag tag) {
            return new Label(tag.getString("name"), tag.getString("phase"), tag.getInt("round"), tag.getInt("rounds"),
                    tag.getString("winner"));
        }
    }

    private UUID event;
    private Label label = Label.NONE;
    private Label drawnLabel;
    private Object drawnLines;

    public ScorekeepersDeskBlockEntity(BlockPos pos, BlockState state) {
        super(GatheringContent.SCOREKEEPERS_DESK_ENTITY.get(), pos, state);
    }

    public Optional<UUID> event() {
        return Optional.ofNullable(event);
    }

    public Label label() {
        return label;
    }

    /** A label shown without a tournament behind it, for a scene that teaches the desk. Client side only. */
    public void showLabel(Label shown) {
        label = shown == null ? Label.NONE : shown;
    }

    public void runs(UUID tournament) {
        if (tournament == null ? event != null : !tournament.equals(event)) {
            event = tournament;
            setChanged();
            if (level instanceof ServerLevel server) {
                refreshLabel(server);
            }
        }
    }

    /** Once a second, on the server: the label brought up to date with the tournament. */
    static void serverTick(ServerLevel level, BlockPos pos, ScorekeepersDeskBlockEntity desk) {
        if ((level.getGameTime() + pos.asLong()) % 20 == 0) {
            desk.refreshLabel(level);
        }
    }

    void refreshLabel(ServerLevel level) {
        Label now = labelNow(level);
        if (!now.equals(label)) {
            label = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private Label labelNow(ServerLevel level) {
        return EventBoard.labelAtDesk(level, worldPosition).map(Label::of).orElse(Label.NONE);
    }

    /** What the renderer draws, kept until the label changes rather than built every frame. Client side. */
    public <T> T linesFor(java.util.function.Function<Label, T> lines) {
        if (drawnLabel != label) {
            drawnLabel = label;
            drawnLines = lines.apply(label);
        }
        @SuppressWarnings("unchecked")
        T kept = (T) drawnLines;
        return kept;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (event != null) {
            tag.putUUID(EVENT_KEY, event);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        event = tag.hasUUID(EVENT_KEY) ? tag.getUUID(EVENT_KEY) : null;
        // Only in what is sent to clients; on the server it is worked out again from the event.
        label = tag.contains(LABEL_KEY) ? Label.load(tag.getCompound(LABEL_KEY)) : Label.NONE;
    }

    /** The label, and nothing else: which tournament a desk runs is the server's business. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Worked out now, not only on the tick: a desk that has just loaded, or stands beyond the
        // distance the server ticks at while inside the distance it is seen from, would otherwise be
        // sent with no label or an old one.
        if (level instanceof ServerLevel server) {
            label = labelNow(server);
        }
        CompoundTag tag = new CompoundTag();
        tag.put(LABEL_KEY, label.save());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
