package dev.gathering.block;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;

/**
 * Whether one of the mod's blocks may be broken by hand, and what to say when it may not.
 *
 * <p>A block cannot decline to be broken in vanilla. By the time {@code playerWillDestroy}
 * runs the decision is already made and the removal happens whatever that method returns,
 * so a guard written there reads like a refusal and is not one. The one place the answer
 * can still be no is the loader's break event, and both loaders have one - which is why
 * the rule lives here in one piece rather than being written out twice and drifting.
 *
 * <p>Only the by-hand path asks. A machine breaking a block has nobody to check and gets
 * the chest answer: it comes out, and everything inside it comes with it.
 */
public final class BreakRules {

    private BreakRules() {
    }

    /**
     * Empty when the break may go ahead, otherwise the reason to show whoever swung.
     *
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
}
