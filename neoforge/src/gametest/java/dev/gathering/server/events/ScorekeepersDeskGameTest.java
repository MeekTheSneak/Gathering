package dev.gathering.server.events;

import dev.gathering.Gathering;
import dev.gathering.block.ScorekeepersDeskBlockEntity;
import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.item.GatheringContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Scorekeeper's Desk: a host using it makes it their tournament's desk and the place signing up
 * happens; anybody else using it leaves it alone; another host using it twice takes it over; breaking
 * it lets signing up go anywhere again. Each through the player's own use of a block, hand and all.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScorekeepersDeskGameTest {

    private static final BlockPos DESK = new BlockPos(1, 2, 1);

    private ScorekeepersDeskGameTest() {
    }

    /** A host's click on a free desk makes it their tournament's, and signing up moves to it. */
    @GameTest(template = "empty")
    public static void aHostsClickMakesADeskTheirs(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        EventState state = hostedBy(helper, host, "Friday Night");
        ServerPlayer passerBy = helper.makeMockServerPlayerInLevel();
        EventState theirs = hostedBy(helper, passerBy, "Saturday");
        try {
            use(helper, host);
            if (!runs(helper, desk, state)) {
                helper.fail("the host's click on a free desk left it running " + deskOf(helper, desk).event());
                return;
            }
            if (!desk.equals(state.registrationPoint)) {
                helper.fail("signing up is at " + state.registrationPoint + ", not the desk at " + desk);
                return;
            }
            EventBoard.Board board = EventBoard.atDesk(helper.getLevel(), desk).orElse(null);
            if (board == null || !board.name().equals("Friday Night") || board.phase() != Tournament.Phase.SIGNUP) {
                helper.fail("the desk's board read " + board);
                return;
            }
            // Another host's click is a look, not a takeover - with something in hand, as a player
            // signing up for constructed holds a deck.
            passerBy.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            use(helper, passerBy);
            if (!runs(helper, desk, state) || theirs.registrationPoint != null) {
                helper.fail("another host's first click took the desk");
                return;
            }
            // A second use long after is a look again.
            long start = Events.wallClock.getAsLong();
            Events.wallClock = () -> start + Events.DESK_SECOND_USE_MILLIS + 1_000;
            use(helper, passerBy);
            if (!runs(helper, desk, state)) {
                helper.fail("a second click long after the first took the desk");
                return;
            }
            // Used again soon after the look, the desk is theirs.
            use(helper, passerBy);
            if (!runs(helper, desk, theirs) || !desk.equals(theirs.registrationPoint)) {
                helper.fail("using the desk again soon after did not take it over");
                return;
            }
            if (state.registrationPoint != null) {
                helper.fail("the tournament that lost its desk still signs players up at it");
                return;
            }
        } finally {
            Events.wallClock = System::currentTimeMillis;
            Events.removeForTesting(state);
            Events.removeForTesting(theirs);
        }
        helper.succeed();
    }

    /**
     * A linked desk carries a label - the tournament's name and where it has got to - which follows
     * the tournament without anybody using the desk again, and leaves when it is called off. What is
     * sent to clients is the label, not which tournament the desk runs.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aLinkedDeskLabelsItsTournament(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        EventState state = hostedBy(helper, host, "Friday Night");
        use(helper, host);
        ScorekeepersDeskBlockEntity entity = deskOf(helper, desk);
        ScorekeepersDeskBlockEntity.Label label = entity.label();
        if (!label.isShown() || !label.name().equals("Friday Night") || !label.phase().equals("signup")) {
            Events.removeForTesting(state);
            helper.fail("a desk just linked is labeled " + label);
            return;
        }
        for (Tournament.Phase phase : Tournament.Phase.values()) {
            String key = "label.gathering.desk." + phase.name().toLowerCase(java.util.Locale.ROOT);
            if (phase != Tournament.Phase.CANCELLED && !net.minecraft.locale.Language.getInstance().has(key)) {
                Events.removeForTesting(state);
                helper.fail("a desk's label has nothing to say for " + phase);
                return;
            }
        }
        CompoundTag sent = entity.getUpdateTag(helper.getLevel().registryAccess());
        if (sent.contains("event") || !sent.contains("label")) {
            Events.removeForTesting(state);
            helper.fail("a desk sends clients " + sent);
            return;
        }
        Events.setForTesting(state, state.tournament.cancel());
        // Nobody uses the desk again: its own tick notices within a second.
        helper.runAfterDelay(25, () -> {
            Events.removeForTesting(state);
            if (entity.label().isShown()) {
                helper.fail("a cancelled tournament's desk is still labeled " + entity.label());
                return;
            }
            helper.succeed();
        });
    }

    /** A host running two tournaments links the one whose tables are beside the desk. */
    @GameTest(template = "empty")
    public static void aHostOfTwoLinksTheOneBesideTheDesk(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        // The far one first, so the order they were made in is not what picks.
        EventState far = hostedBy(helper, host, "Across the Hall", List.of(desk.offset(60, 0, 0)));
        EventState near = hostedBy(helper, host, "Here", List.of(desk.offset(0, 0, 3)));
        try {
            use(helper, host);
            if (!runs(helper, desk, near)) {
                helper.fail("the desk linked " + deskOf(helper, desk).event() + " rather than the tournament beside it");
                return;
            }
            // Using it again only looks, even though the host has another tournament without a desk.
            use(helper, host);
            if (!runs(helper, desk, near) || far.registrationPoint != null) {
                helper.fail("the host's second click moved their own desk to their other tournament");
                return;
            }
        } finally {
            Events.removeForTesting(far);
            Events.removeForTesting(near);
        }
        helper.succeed();
    }

    /** A host's own desk takes back signing up that was moved elsewhere - or a desk carried somewhere new. */
    @GameTest(template = "empty")
    public static void aHostsOwnDeskTakesSigningUpBack(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        EventState state = hostedBy(helper, host, "Friday Night");
        try {
            use(helper, host);
            state.registrationPoint = desk.offset(20, 0, 0);
            use(helper, host);
            if (!desk.equals(state.registrationPoint)) {
                helper.fail("the host's own desk left signing up at " + state.registrationPoint);
                return;
            }
        } finally {
            Events.removeForTesting(state);
        }
        helper.succeed();
    }

    /** Somebody hosting nothing only looks: a desk is never linked by a passer-by. */
    @GameTest(template = "empty")
    public static void aPlayerHostingNothingOnlyLooks(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        EventState state = hostedBy(helper, host, "Friday Night");
        try {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setShiftKeyDown(true);
            use(helper, player);
            if (deskOf(helper, desk).event().isPresent() || state.registrationPoint != null) {
                helper.fail("a player hosting nothing linked the desk");
                return;
            }
            if (EventBoard.atDesk(helper.getLevel(), desk).isPresent()) {
                helper.fail("a desk running nothing read a tournament");
                return;
            }
        } finally {
            Events.removeForTesting(state);
        }
        helper.succeed();
    }

    /** A finished tournament's desk is free for the next, without sneaking. */
    @GameTest(template = "empty")
    public static void aFinishedTournamentsDeskIsFree(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper);
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        EventState over = hostedBy(helper, first, "Last Week");
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        EventState next = hostedBy(helper, second, "This Week");
        try {
            use(helper, first);
            Events.setForTesting(over, over.tournament.cancel());
            use(helper, second);
            if (!runs(helper, desk, next)) {
                helper.fail("a cancelled tournament kept its desk from the next host");
                return;
            }
        } finally {
            Events.removeForTesting(over);
            Events.removeForTesting(next);
        }
        helper.succeed();
    }

    /** Breaking the desk takes signing up off the spot it stood on. */
    @GameTest(template = "empty")
    public static void breakingTheDeskFreesSigningUp(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        EventState state = hostedBy(helper, host, "Friday Night");
        try {
            use(helper, host);
            if (!desk.equals(state.registrationPoint)) {
                helper.fail("the desk never became the place to sign up");
                return;
            }
            helper.getLevel().destroyBlock(desk, false);
            if (state.registrationPoint != null) {
                helper.fail("signing up is still at " + state.registrationPoint + ", where the desk was");
                return;
            }
        } finally {
            Events.removeForTesting(state);
        }
        helper.succeed();
    }

    /** A lectern's look and a lectern's shape: not a full cube hiding its neighbors' faces or blocking light. */
    @GameTest(template = "empty")
    public static void aDeskIsShapedLikeWhatItLooksLike(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper);
        var state = helper.getLevel().getBlockState(desk);
        if (net.minecraft.world.level.block.Block.isShapeFullBlock(state.getOcclusionShape(helper.getLevel(), desk))
                || net.minecraft.world.level.block.Block.isShapeFullBlock(state.getCollisionShape(helper.getLevel(), desk))
                || !state.useShapeForLightOcclusion()) {
            helper.fail("the desk is a full cube where it shows a lectern");
            return;
        }
        helper.succeed();
    }

    /** Which tournament a desk runs is saved with it. */
    @GameTest(template = "empty")
    public static void aDeskRemembersItsTournament(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper);
        UUID id = new UUID(4L, 2L);
        ScorekeepersDeskBlockEntity entity = deskOf(helper, desk);
        entity.runs(id);
        CompoundTag saved = entity.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.getLevel().removeBlock(desk, false);
        placeDesk(helper);
        ScorekeepersDeskBlockEntity again = deskOf(helper, desk);
        if (again.event().isPresent()) {
            helper.fail("a new desk came with a tournament");
            return;
        }
        again.loadWithComponents(saved, helper.getLevel().registryAccess());
        if (!again.event().equals(java.util.Optional.of(id))) {
            helper.fail("the desk loaded " + again.event() + " rather than " + id);
            return;
        }
        helper.succeed();
    }

    /** A use of the desk the way a player's click reaches it, hand and all. */
    private static void use(GameTestHelper helper, ServerPlayer player) {
        BlockPos at = helper.absolutePos(DESK);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    private static BlockPos placeDesk(GameTestHelper helper) {
        helper.setBlock(DESK, GatheringContent.SCOREKEEPERS_DESK.get().defaultBlockState()
                .setValue(dev.gathering.block.ScorekeepersDeskBlock.FACING, Direction.SOUTH));
        return helper.absolutePos(DESK);
    }

    private static ScorekeepersDeskBlockEntity deskOf(GameTestHelper helper, BlockPos desk) {
        if (helper.getLevel().getBlockEntity(desk) instanceof ScorekeepersDeskBlockEntity entity) {
            return entity;
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("no desk at " + desk);
    }

    private static boolean runs(GameTestHelper helper, BlockPos desk, EventState state) {
        return deskOf(helper, desk).event().equals(java.util.Optional.of(state.tournament.id()));
    }

    private static EventState hostedBy(GameTestHelper helper, ServerPlayer host, String name) {
        return hostedBy(helper, host, name, List.of());
    }

    private static EventState hostedBy(GameTestHelper helper, ServerPlayer host, String name, List<BlockPos> tables) {
        Tournament tournament = Tournament.create(UUID.randomUUID(), name, host.getUUID(),
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        EventState state = Events.stateForTesting(tournament, helper.getLevel(), tables);
        Events.putForTesting(state);
        return state;
    }
}
