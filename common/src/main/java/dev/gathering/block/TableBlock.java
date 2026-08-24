package dev.gathering.block;

import com.mojang.serialization.MapCodec;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.format.FormatPreset;
import dev.gathering.core.format.ValidationIssue;
import dev.gathering.core.format.ValidationResult;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
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
            sitDownAndPlay(level, tableOrigin, player, stack, side);
            return ItemInteractionResult.SUCCESS;
        }

        // Clicking the edge you are already sitting at used to give up your chair, which
        // swallowed the one click a seated player most wants to make. Their own side of the
        // table is where they stand, so opening the board was reachable only by walking round
        // to somebody else's chair first. Standing up is on the board's own menu now, where
        // the rest of the seat verbs live, and this click falls through to opening it.
        boolean alreadySeatedHere = side != null
                && TableSeats.seatOf(level, tableOrigin, player.getUUID())
                        .filter(seat -> seat.cell().equals(cell) && seat.side() == side)
                        .isPresent();

        if (!alreadySeatedHere && side != null && TableSeats.isSeat(cluster, cell, side)) {
            TableSeats.Claim claim = TableSeats.take(level, tableOrigin, cell, side, player.getUUID());
            player.sendSystemMessage(Component.translatable(claim.messageKey()));
            tellTheTableWhoIsSittingAtIt(level, tableOrigin);
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
     * Everything between holding a deck and playing with it, in one gesture.
     *
     * <p>It used to be four: sit at an edge, crouch to ask for a game, pick a format from a
     * screen, then click again with the deck. Every one of them is a thing to get wrong in
     * order, and getting them wrong in order is most of what "it doesn't work" means to
     * somebody trying a mod for the first time. Walking up to a table holding a deck says what
     * you want as clearly as anybody ever says anything.
     *
     * <p>The format prompt has not gone anywhere - crouching still asks, which is the
     * deliberate gesture for a table that wants to be something other than the usual. It is
     * just no longer in the way of the usual.
     */
    private static void sitDownAndPlay(
            Level level, BlockPos tableOrigin, Player player, ItemStack stack, Side side) {
        if (!sitDownIfNeeded(level, tableOrigin, player, side)) {
            return;
        }
        if (!TableSessions.hasSession(level, tableOrigin)) {
            // Between games of a set is not the same as no game here. Treating them alike
            // started a fresh Commander game on top of a best-of-three, which threw away the
            // score, the format the table had been playing and the sideboard step, and dealt
            // everybody's held deck back out as though the match had never happened.
            if (level instanceof net.minecraft.server.level.ServerLevel server
                    && player instanceof net.minecraft.server.level.ServerPlayer asking
                    && dev.gathering.server.TableMatch.isBetweenGames(server, tableOrigin)) {
                dev.gathering.server.TableMatch.startNextGame(server, tableOrigin, asking);
                return;
            }
            TableSessions.Outcome outcome = TableSessions.start(
                    level, tableOrigin, MatchRules.single(FormatPresets.COMMANDER));
            if (outcome != TableSessions.Outcome.STARTED) {
                player.sendSystemMessage(Component.translatable(outcome.messageKey()));
                return;
            }
        }
        commitDeck(level, tableOrigin, player, stack);
    }

    /**
     * Sends the board out again after somebody has sat down or stood up.
     *
     * <p>What a client is shown depends on whether it has a seat: a seated player gets their
     * own hand and their own library's shape, and everybody else gets the public board. So the
     * moment somebody takes or gives up a seat, the board they are holding is the wrong one.
     *
     * <p>It corrected itself eventually - the ambient beat sends the public board to the room
     * every so often - which is the worst of both: a player who sat down mid-game watched
     * their own hand refuse to appear for a second or two, and a player who stood up went on
     * looking at cards they were no longer entitled to until the tick came round.
     */
    private static void tellTheTableWhoIsSittingAtIt(Level level, BlockPos tableOrigin) {
        if (level instanceof net.minecraft.server.level.ServerLevel server
                && TableSessions.hasSession(level, tableOrigin)) {
            dev.gathering.server.TableBroadcast.sendToTable(server, tableOrigin);
        }
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
        return sitDownIfNeeded(level, tableOrigin, player, null);
    }

    /**
     * @param preferred the edge the player was actually at, tried before anything else - so
     *     walking up to one side of a four-seat pod and putting a deck down sits you at that
     *     side rather than at whichever chair happens to come first in cluster order
     */
    private static boolean sitDownIfNeeded(
            Level level, BlockPos tableOrigin, Player player, Side preferred) {
        if (TableSeats.seatOf(level, tableOrigin, player.getUUID()).isPresent()) {
            return true;
        }
        List<SeatAnchor> anchors = new java.util.ArrayList<>(
                TableClusters.at(level, tableOrigin).seats());
        if (preferred != null) {
            TableCell here = new TableCell(0, 0);
            anchors.sort(java.util.Comparator.comparingInt(
                    anchor -> anchor.side() == preferred && anchor.cell().equals(here) ? 0 : 1));
        }
        for (SeatAnchor anchor : anchors) {
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
        if (!deckMayGoDown(level, tableOrigin, deck, player)) {
            return;
        }
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

        // Everyone at the table, then the board for whoever just joined. Only opening it for
        // the player who clicked left every other seat - and the miniature on the table top,
        // which is what anybody walking past sees - still showing the game as it was before a
        // deck went down.
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            dev.gathering.server.TableBroadcast.sendToTable(server, tableOrigin);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer joined) {
            dev.gathering.server.TableActions.openFor(joined, tableOrigin);
        }
    }

    /**
     * The deck check, and what a player is told when their deck does not pass it.
     *
     * <p>Before anything happens to the deck: it stays in their hand, the seat stays theirs,
     * and the table stays where it was. A refusal that also ate the deck would be a refusal
     * nobody could act on.
     *
     * <p>Only errors stop a game, and only on a table somebody chose a format for. A deck
     * check is a tournament deck check, and a tournament deck check happens because somebody
     * entered a tournament: walking up to a bare table holding a deck says "let me play", not
     * "hold me to Commander". The table has to start with some rules and it starts with
     * Commander, so a deck that fails there is told what is wrong and dealt out anyway. Pick
     * a format off the setup screen and the same failure is a refusal.
     *
     * <p>A warning is never either of those - it is the check noticing something odd, like
     * commanders listed for a format with no command zone - and nothing stops for odd.
     *
     * <p>Named and public so a test can ask the question a right-click asks, rather than
     * reaching past it to the validator and leaving the two lines that actually consult it
     * untested - which is how the validator came to be wired to nothing in the first place.
     *
     * @return whether the deck may go down
     */
    public static boolean deckMayGoDown(
            Level level, BlockPos tableOrigin, DeckComponent deck, Player player) {
        TableBlockEntity table = TableSessions.anchorOf(level, tableOrigin)
                .flatMap(anchor -> entityAt(level, anchor))
                .orElse(null);
        FormatPreset format = table == null ? null : table.match()
                .map(match -> match.rules().format())
                .orElse(null);
        ValidationResult result = dev.gathering.server.DeckCheck.of(deck, format).orElse(null);
        if (result == null || result.isLegal()) {
            return true;
        }
        boolean refusing = table != null && table.formatWasChosen();
        player.sendSystemMessage(Component.translatable(
                refusing ? "message.gathering.deck_illegal" : "message.gathering.deck_odd",
                format.displayName()));
        List<ValidationIssue> errors = result.errors();
        for (int index = 0; index < Math.min(errors.size(), MOST_PROBLEMS_WORTH_LISTING); index++) {
            player.sendSystemMessage(Component.literal("  " + errors.get(index).message()));
        }
        if (errors.size() > MOST_PROBLEMS_WORTH_LISTING) {
            player.sendSystemMessage(Component.translatable("message.gathering.deck_illegal_more",
                    errors.size() - MOST_PROBLEMS_WORTH_LISTING));
        }
        if (refusing) {
            player.sendSystemMessage(Component.translatable("message.gathering.deck_illegal_hint"));
        }
        return !refusing;
    }

    /**
     * How much of a bad deck to read out.
     *
     * <p>A sixty-card deck built for the wrong format produces sixty problems, and sixty lines
     * of chat is not a message - it is a wall that pushes everything else off the screen. Five
     * is enough to see what kind of wrong it is.
     */
    private static final int MOST_PROBLEMS_WORTH_LISTING = 5;

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
