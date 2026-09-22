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
        // Nor a chair somebody else is sitting in at a game: breaking it gets them up out of their seat.
        if (level.getBlockState(pos).getBlock() instanceof ChairBlock && level instanceof net.minecraft.world.level.Level world) {
            for (ChairSeat seat : world.getEntitiesOfClass(ChairSeat.class, new net.minecraft.world.phys.AABB(pos))) {
                if (seat.tableOrigin() != null && TableSessions.hasSession(world, seat.tableOrigin())
                        && seat.getPassengers().stream().anyMatch(sitter -> !sitter.getUUID().equals(player.getUUID()))) {
                    return Optional.of(Component.translatable("message.gathering.chair_in_use"));
                }
            }
        }
        if (level.getBlockEntity(pos) instanceof CollectionBlockEntity collection
                && !collection.rights().mayTake(player.getUUID())) {
            return Optional.of(
                    Component.translatable("message.gathering.collection_may_not_take"));
        }
        // Nor somebody else's display case with a card in it, which would be a card taken by breaking
        // the glass rather than by asking - the same rule the collection has, for the same reason.
        if (level.getBlockEntity(pos) instanceof DisplayCaseBlockEntity display
                && !display.isEmpty() && !display.isOwner(player.getUUID())) {
            return Optional.of(Component.translatable("message.gathering.display_case_not_yours"));
        }
        return Optional.empty();
    }

    /**
     * Whether an explosion has to leave this block where it is.
     * <p>A creeper wandering into a shop is not an argument anybody was having. Everything the
     * by-hand rules above refuse is refused because somebody is using it or something of theirs is
     * inside it, and none of those reasons stops being true because the thing that arrived was TNT
     * instead of a pickaxe - so the same list, minus the parts that need a player to ask about.
     * <p>The owner's report was a table blown up mid-game (2026-09-22) and his instruction was to
     * look for the rest of it, which is what this is: a seat somebody is in, a tournament being
     * scored, a cabinet with cards in it, a case with a card on show, and a trophy somebody won.
     * <p>Deliberately not every block the mod has. An empty table, a spare chair and a bare
     * counter are furniture, and furniture in a world with TNT in it is furniture that can be
     * blown up - making the mod's blocks blast-proof as a family would be a mod deciding how
     * somebody's world works.
     */
    public static boolean survivesExplosions(BlockGetter level, BlockPos pos) {
        if (!TableSeats.mayBreak(level, pos)) {
            return true;
        }
        if (level instanceof net.minecraft.world.level.Level world) {
            // A chair with somebody in it, whether or not a game is on: blowing it up stands them
            // out of a seat they were sitting in, which is the same thing breaking it by hand is
            // refused for.
            if (level.getBlockState(pos).getBlock() instanceof ChairBlock
                    && !world.getEntitiesOfClass(
                            ChairSeat.class, new net.minecraft.world.phys.AABB(pos)).isEmpty()) {
                return true;
            }
            // A table of a cluster with a game on, wherever in the cluster the charge went off.
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof TableBlock
                    && TableSessions.hasSession(world, TableBlock.originOf(state, pos))) {
                return true;
            }
        }
        net.minecraft.world.level.block.entity.BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof CollectionBlockEntity collection) {
            return !collection.cards().isEmpty();
        }
        if (entity instanceof DisplayCaseBlockEntity display) {
            return !display.isEmpty();
        }
        if (entity instanceof ScorekeepersDeskBlockEntity desk) {
            return desk.event().isPresent();
        }
        if (entity instanceof TrophyBlockEntity trophy) {
            return trophy.engraved().isEngraved();
        }
        return false;
    }

    /**
     * How hard this block is for an explosion to take, given the above.
     * <p>Obsidian's own number, near enough: the point is not that it is stronger than TNT by some
     * margin, it is that it is not going anywhere.
     */
    public static float explosionResistance(BlockGetter level, BlockPos pos, float ordinarily) {
        return survivesExplosions(level, pos) ? 1200f : ordinarily;
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
