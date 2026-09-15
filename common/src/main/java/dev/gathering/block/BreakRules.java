package dev.gathering.block;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;

/**
 * Whether one of the mod's blocks may be broken by hand, and what to say when it may not.
 * <p>A block cannot decline to be broken in vanilla. By the time {@code playerWillDestroy}
 * runs the decision is already made and the removal happens whatever that method returns,
 * so a guard written there reads like a refusal and is not one. The one place the answer
 * can still be no is the loader's break event, and both loaders have one - which is why
 * the rule lives here in one piece rather than being written out twice and drifting.
 * <p>Only the by-hand path asks. A machine breaking a block has nobody to check and gets
 * the chest answer: it comes out, and everything inside it comes with it.
 */
public final class BreakRules {

    private BreakRules() {
    }

    /**
     * Empty when the break may go ahead, otherwise the reason to show whoever swung.
     * <p>Ordered the way a player would notice: a table with somebody at it is refused
     * before its contents are considered at all.
     */
    public static Optional<Component> refuse(BlockGetter level, BlockPos pos, Player player) {
        if (!TableSeats.mayBreak(level, pos)) {
            return Optional.of(Component.translatable("message.gathering.table_in_use"));
        }
        if (level.getBlockEntity(pos) instanceof CollectionBlockEntity collection
                && !collection.rights().mayTake(player.getUUID())) {
            return Optional.of(
                    Component.translatable("message.gathering.collection_may_not_take"));
        }
        return Optional.empty();
    }

    /**
     * After a break was refused: sends the table the block belongs to out to clients again, block and
     * everything the table carries.
     * <p>The client does not wait to be told. It breaks the block the moment the swing lands, and for a
     * table's corner that throws away the client's copy of the table - the felt, whether it has a
     * command zone, which way it is turned. The server's refusal puts the block back and nothing else,
     * so the table came back as a blank one: a Commander game carried on with its command zone gone.
     * <p>A couple of ticks later rather than now. The client holds a block it broke itself until the
     * server acknowledges the swing, and anything about the table sent before that acknowledgement
     * arrives finds no table to apply to. Sent straight away, it fixed nothing.
     */
    public static void refused(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || !(state.getBlock() instanceof TableBlock)) {
            return;
        }
        BlockPos origin = TableBlock.originOf(state, pos).immutable();
        dev.gathering.server.ServerTicks.on(java.util.List.of("refused break", server.dimension(), origin),
                server.getServer().getTickCount() + TICKS_UNTIL_ACKNOWLEDGED, () -> {
                    net.minecraft.world.level.block.state.BlockState corner = server.getBlockState(origin);
                    if (corner.getBlock() instanceof TableBlock) {
                        server.sendBlockUpdated(origin, corner, corner, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    }
                });
    }

    /** How long after a refused swing the client has certainly been told, in ticks. */
    private static final int TICKS_UNTIL_ACKNOWLEDGED = 2;
}
