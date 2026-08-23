package dev.gathering.block;

import com.mojang.serialization.MapCodec;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.VisibilityRules;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.core.table.SeatAnchor;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.core.table.TableCluster;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The table: two blocks by two, and the thing a game of cards happens on.
 *
 * <p>Four blocks, one of which - the north-west corner - carries the block entity and owns
 * the session. Breaking any quarter takes the whole table with it, in the way a bed or a door
 * does, because a table missing a corner is not a smaller table.
 *
 * <p>Tables pushed edge to edge become one cluster running one session. Which tables join, how
 * many seats that makes and where the seats go is all decided in {@code :core} and reached
 * through {@link TableClusters}, so what happens in a world is the arithmetic that was tested
 * rather than a second copy of it.
 */
public class TableBlock extends BaseEntityBlock {

    public static final EnumProperty<TablePart> PART = EnumProperty.create("part", TablePart.class);

    public static final MapCodec<TableBlock> CODEC = simpleCodec(TableBlock::new);

    public TableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, TablePart.origin()));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Only the corner that owns the table gets one. Three empty block entities per table
        // would tick, save and load for nothing.
        return state.getValue(PART).isOrigin() ? new TableBlockEntity(pos, state) : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Solid up to the felt, so you stand on the surface rather than a pixel above it.
     *
     * <p>Square-sided rather than following the legs: a shape with a gap under it is a shape
     * a player can walk into and a card can fall through, and neither is worth the accuracy.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);

    /**
     * Only the corner that owns the table ticks, and only when there is a game on it.
     *
     * <p>Three empty block entities per table ticking for nothing is the sort of cost that
     * does not show up until somebody builds a shop full of tables.
     */
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide() || !state.getValue(PART).isOrigin()) {
            return null;
        }
        return (ticking, pos, ticked, entity) -> {
            if (entity instanceof TableBlockEntity table && table.hasSession()) {
                TableBlockEntity.serverTick(ticking, pos, ticked, table);
            }
        };
    }

    /** The corner that owns the table this block is part of. */
    public static BlockPos originOf(BlockState state, BlockPos pos) {
        return state.getValue(PART).originFrom(pos);
    }

    /** The block entity that owns the table this position is part of, if it is one. */
    public static Optional<TableBlockEntity> entityAt(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TableBlock)) {
            return Optional.empty();
        }
        BlockEntity entity = level.getBlockEntity(originOf(state, pos));
        return entity instanceof TableBlockEntity table ? Optional.of(table) : Optional.empty();
    }

    /**
     * Whether a table can be placed with its corner here.
     *
     * <p>Two questions, both of which have to be answered before anything is placed: is there
     * room for four blocks, and would joining what is already there make a cluster bigger than
     * a cluster is allowed to be.
     */
    public static boolean canPlaceAt(BlockPlaceContext context, BlockPos origin) {
        for (TablePart part : TablePart.values()) {
            BlockPos pos = part.offsetFrom(origin);
            if (!context.getLevel().getBlockState(pos).canBeReplaced(context)) {
                return false;
            }
            if (!context.getLevel().getWorldBorder().isWithinBounds(pos)) {
                return false;
            }
        }
        if (!TableClusters.wouldFit(context.getLevel(), origin)) {
            return false;
        }
        // Joining a cluster reshapes its perimeter, which moves its seats. Somebody
        // registered at an edge should not find that edge is now the middle of the surface.
        for (Side side : Side.values()) {
            BlockPos neighbour = origin.offset(
                    side.stepX() * TableCell.BLOCKS_PER_TABLE, 0, side.stepZ() * TableCell.BLOCKS_PER_TABLE);
            if (context.getLevel() instanceof Level level
                    && TableBlock.entityAt(level, neighbour).isPresent()
                    && TableSeats.isShapeFrozen(level, neighbour)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Takes the rest of the table with it.
     *
     * <p>Guarded by checking the neighbour really is the rest of this table: without that,
     * removing one table's corner would take a bite out of the table pushed up against it.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            spillDecks(level, pos, state);
            removeRestOfTable(level, pos, state);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            spillDecks(level, pos, state);
            removeRestOfTable(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /**
     * Gives back any decks the table was holding before it stops existing.
     *
     * <p>A table cannot be broken while anybody is sitting at it, so this is the case where
     * everyone has walked away from an unfinished match. The decks are still theirs, and a
     * table that ate four of them because the last player left is not a table anybody should
     * put a deck on twice.
     */
    private static void spillDecks(Level level, BlockPos pos, BlockState state) {
        BlockPos origin = originOf(state, pos);
        TableSessions.anchorOf(level, origin)
                .flatMap(anchor -> entityAt(level, anchor))
                .ifPresent(table -> TableSessions.returnDecks(level, origin, table));
    }

    private static void removeRestOfTable(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockPos origin = originOf(state, pos);
        for (TablePart part : TablePart.values()) {
            BlockPos other = part.offsetFrom(origin);
            if (other.equals(pos)) {
                continue;
            }
            BlockState there = level.getBlockState(other);
            if (there.getBlock() instanceof TableBlock && there.getValue(PART) == part) {
                level.setBlock(other, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }

    /**
     * Right-click an edge to take that seat, or your own seat to leave it.
     *
     * <p>The edge you clicked, not the nearest free one: a seat is a place at a table and
     * which place you take is the one social decision this interaction carries. Clicking the
     * top of the table, or an edge that is not a seat, says what the cluster is instead -
     * which for now is also the only way to see that cluster shape and capacity are right in
     * a world, there being no seated view to sit down into yet.
     */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockPos tableOrigin = originOf(state, pos);

        // Dye in hand means you came to change the felt, not to sit down.
        if (stack.getItem() instanceof DyeItem dye) {
            boolean changed = entityAt(level, pos)
                    .map(table -> table.dye(dye.getDyeColor()))
                    .orElse(false);
            if (changed && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }

        // The cluster is worked out relative to the table that was clicked, so that table is
        // always its origin cell. Only outward faces of a table are reachable, so the face
        // clicked is the edge meant.
        TableCluster cluster = TableClusters.at(level, tableOrigin);
        TableCell cell = new TableCell(0, 0);
        Side side = TableClusters.sideFrom(hit.getDirection(), player.position(), tableOrigin);

        // What is in your hand decides what a click means, before where you clicked does.
        // A deck in hand and a table in front of you is one thing and only one thing, and it
        // used to be read as "stand up": the seat toggle came first, saw the player already
        // sitting at the edge they were standing at, and gave up their chair instead of taking
        // their deck. Which then left them spectating their own game, with every action
        // refused, for reasons nothing on screen explained.
        if (DeckItem.deckOf(stack).isPresent()) {
            commitDeck(level, tableOrigin, player, stack);
            return ItemInteractionResult.SUCCESS;
        }

        if (side != null && TableSeats.seatOf(level, tableOrigin, player.getUUID())
                .filter(seat -> seat.cell().equals(cell) && seat.side() == side).isPresent()) {
            TableSeats.leave(level, tableOrigin, player.getUUID());
            player.sendSystemMessage(Component.translatable("message.gathering.seat_left"));
            return ItemInteractionResult.SUCCESS;
        }

        if (side != null && TableSeats.isSeat(cluster, cell, side)) {
            TableSeats.Claim claim = TableSeats.take(level, tableOrigin, cell, side, player.getUUID());
            player.sendSystemMessage(Component.translatable(claim.messageKey()));
            return ItemInteractionResult.SUCCESS;
        }

        // Crouching is the deliberate gesture, because starting a game is a deliberate act.
        // It asks rather than starts: which format and how many games is the difference
        // between a Commander pod and a best-of-three of Modern, and picking one for the
        // table picks a format to be the real one.
        if (player.isShiftKeyDown()) {
            startOrContinue(level, tableOrigin, player);
            return ItemInteractionResult.SUCCESS;
        }

        // A game running here means you came to play it, not to read a summary of it.
        if (player instanceof net.minecraft.server.level.ServerPlayer seated
                && TableSessions.hasSession(level, tableOrigin)) {
            dev.gathering.server.TableActions.openFor(seated, tableOrigin);
            return ItemInteractionResult.SUCCESS;
        }

        // Between games of a set, the same click opens your deck to change it. Same gesture,
        // because "open the table" is what a player means either way and the table knows
        // which of those it currently is.
        if (player instanceof net.minecraft.server.level.ServerPlayer between
                && level instanceof net.minecraft.server.level.ServerLevel server
                && dev.gathering.server.TableMatch.isBetweenGames(server, tableOrigin)) {
            if (dev.gathering.server.TableMatch.isSideboarding(server, tableOrigin)) {
                dev.gathering.server.Sideboarding.offerTo(between, tableOrigin);
            } else {
                between.sendSystemMessage(
                        Component.translatable("message.gathering.next_game_ready"));
            }
            return ItemInteractionResult.SUCCESS;
        }

        report(level, tableOrigin, cluster, player);
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * Crouching on a table: either "what shall we play" or "next game, please".
     *
     * <p>Which one depends on whether a set is already running here. Asking the format again
     * between games of a best-of-three would be asking a question that has been answered, and
     * offering to change it mid-set is offering to make the score meaningless.
     */
    /**
     * The crouch gesture, reachable without a click.
     *
     * <p>Named and public so a test can perform the thing a player performs rather than the
     * pieces underneath it - the seating and the starting are one gesture and the bug worth
     * catching is in how they fit together.
     */
    public static void startGameFor(Level level, BlockPos tableOrigin, Player player) {
        startOrContinue(level, tableOrigin, player);
    }

    private static void startOrContinue(Level level, BlockPos tableOrigin, Player player) {
        if (TableSessions.hasSession(level, tableOrigin)) {
            player.sendSystemMessage(Component.translatable("message.gathering.session_already_running"));
            return;
        }
        // Sitting down comes first and needs nothing but a world. Only the asking - which is
        // a packet - needs a connection to ask down, so a player without one still ends up in
        // a seat rather than being turned away before anything happened.
        if (!sitDownIfNeeded(level, tableOrigin, player)) {
            return;
        }
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)
                || !(player instanceof net.minecraft.server.level.ServerPlayer asking)) {
            return;
        }
        if (dev.gathering.server.TableMatch.isBetweenGames(server, tableOrigin)) {
            dev.gathering.server.TableMatch.startNextGame(server, tableOrigin, asking);
            return;
        }
        dev.gathering.server.TableSetup.ask(asking, tableOrigin);
    }

    /**
     * Puts the player in a seat if they are not already in one.
     *
     * <p>Somebody crouching on a table to start a game has said what they want clearly enough.
     * Refusing because they had not performed the separate ceremony of clicking an edge first
     * is the kind of thing that makes a mod look broken to the person trying it alone - which
     * is everybody, the first time.
     *
     * <p>Only ever takes a free seat, and never moves somebody who already has one.
     *
     * @return whether they now have a seat
     */
    private static boolean sitDownIfNeeded(Level level, BlockPos tableOrigin, Player player) {
        if (TableSeats.seatOf(level, tableOrigin, player.getUUID()).isPresent()) {
            return true;
        }
        for (SeatAnchor anchor : TableClusters.at(level, tableOrigin).seats()) {
            TableSeats.Claim claim = TableSeats.take(
                    level, tableOrigin, anchor.cell(), anchor.side(), player.getUUID());
            if (claim == TableSeats.Claim.TAKEN) {
                player.sendSystemMessage(Component.translatable("message.gathering.seat_taken"));
                return true;
            }
        }
        player.sendSystemMessage(Component.translatable("message.gathering.table_full"));
        return false;
    }

    /**
     * Puts a deck into the game, then shuffles it.
     *
     * <p>Two events rather than one, because they are two different things and the log should
     * say so: an unshuffled deck is an unshuffled deck, and "shuffled" is a line somebody at
     * the table is entitled to see happen.
     */
    private static void commitDeck(Level level, BlockPos tableOrigin, Player player, ItemStack stack) {
        GameSession session = TableSessions.sessionAt(level, tableOrigin).orElse(null);
        if (session == null) {
            player.sendSystemMessage(Component.translatable("message.gathering.session_not_running"));
            return;
        }
        SeatId seat = TableSessions.seatIdOf(level, tableOrigin, player.getUUID()).orElse(null);
        if (seat == null) {
            player.sendSystemMessage(Component.translatable("message.gathering.deck_needs_seat"));
            return;
        }

        GameView view = VisibilityRules.viewFor(session.state(), new Viewer.Seated(seat));
        if (view.seat(seat).zone(Zone.LIBRARY).count() > 0) {
            player.sendSystemMessage(Component.translatable("message.gathering.deck_already_down"));
            return;
        }

        DeckComponent deck = DeckItem.deckOf(stack).orElseThrow();
        List<CardIdentity> library = deck.entries().stream().map(CardComponent::toIdentity).toList();
        List<CardIdentity> commanders = deck.commanders().stream().map(CardComponent::toIdentity).toList();

        session.submit(new GameEvent.DeckLoaded(seat, library, commanders));
        session.submit(new GameEvent.LibraryShuffled(seat, seat));

        // The table takes the deck rather than the game eating it. The sideboard never went
        // into the session and never could - it is not in play - so without somewhere to keep
        // it, committing a deck destroyed a quarter of it and ending the game destroyed the
        // rest. The table hands the whole thing back when the match is over.
        TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> entityAt(level, anchor))
                .ifPresent(table -> table.holdDeck(seat, deck));

        stack.shrink(1);
        player.sendSystemMessage(Component.translatable(
                "message.gathering.deck_committed", deck.deckSize()));
    }

    /** What is going on here, said to whoever asked and filtered to what they may know. */
    private static void report(Level level, BlockPos tableOrigin, TableCluster cluster, Player player) {
        player.sendSystemMessage(Component.translatable(
                "message.gathering.table_status",
                cluster.tableCount(),
                TableSeats.occupiedSeats(level, tableOrigin),
                cluster.capacity()));

        TableSessions.sessionAt(level, tableOrigin).ifPresent(session -> {
            Viewer viewer = TableStatus.viewerFor(
                    TableSessions.seatIdOf(level, tableOrigin, player.getUUID()));
            TableStatus.describe(VisibilityRules.viewFor(session.state(), viewer))
                    .forEach(player::sendSystemMessage);
        });
    }
}
