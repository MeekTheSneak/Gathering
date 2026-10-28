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

    /**
     * Hosting is done at a desk: the tournament plays at the free tables near it, leaving out one another
     * tournament plays at, and the desk runs it from the start. A desk running a tournament that is not
     * over hosts nothing more.
     */
    @GameTest(template = "tables")
    public static void aDeskHostsATournamentAtTheTablesNearIt(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper);
        BlockPos near = EventBoardGameTest.place(helper, 4, 2, 4);
        BlockPos taken = EventBoardGameTest.place(helper, 4, 2, 9);
        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        EventState elsewhere = hostedBy(helper, other, "Elsewhere", List.of(taken));
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        host.moveTo(Vec3.atCenterOf(desk).add(0, 0, 1.5));
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        second.moveTo(Vec3.atCenterOf(desk).add(0, 0, 1.5));
        EventState[] hosted = new EventState[2];
        try {
            EventViews.create(host, new dev.gathering.network.CreateEventPayload(desk, "Friday Night",
                    EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"), List.of()));
            hosted[0] = Events.all().stream().filter(state -> state.tournament.host().equals(host.getUUID()))
                    .findFirst().orElse(null);
            if (hosted[0] == null) {
                helper.fail("hosting at a desk created no tournament");
                return;
            }
            if (!hosted[0].tables.equals(List.of(near))) {
                helper.fail("a tournament hosted at a desk plays at " + hosted[0].tables + ", not the free table " + near);
                return;
            }
            if (!runs(helper, desk, hosted[0]) || !desk.equals(hosted[0].registrationPoint)) {
                helper.fail("the desk a tournament was hosted at does not run it: " + deskOf(helper, desk).event()
                        + ", signing up at " + hosted[0].registrationPoint);
                return;
            }
            EventViews.create(second, new dev.gathering.network.CreateEventPayload(desk, "Saturday",
                    EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"), List.of()));
            hosted[1] = Events.all().stream().filter(state -> state.tournament.host().equals(second.getUUID()))
                    .findFirst().orElse(null);
            if (hosted[1] != null || !runs(helper, desk, hosted[0])) {
                helper.fail("a desk running a tournament hosted another at it");
                return;
            }
        } finally {
            Events.removeForTesting(elsewhere);
            for (EventState state : hosted) {
                if (state != null) {
                    Events.removeForTesting(state);
                }
            }
        }
        helper.succeed();
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
        java.util.List<String> phases = new java.util.ArrayList<>(java.util.List.of("signup_elsewhere", "over", "time"));
        for (Tournament.Phase phase : Tournament.Phase.values()) {
            if (phase != Tournament.Phase.CANCELLED) {
                phases.add(phase.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (String phase : phases) {
            String key = "label.gathering.desk." + phase;
            if (!net.minecraft.locale.Language.getInstance().has(key)) {
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
        // Signing up moved to another spot: this desk no longer says it happens here - and says so
        // in what it sends a client arriving now, not only after its next tick.
        state.registrationPoint = desk.offset(12, 0, 0);
        String sentPhase = entity.getUpdateTag(helper.getLevel().registryAccess()).getCompound("label").getString("phase");
        if (!sentPhase.equals("signup_elsewhere")) {
            Events.removeForTesting(state);
            helper.fail("a desk signing up has moved away from sends the label " + sentPhase);
            return;
        }
        // Worked out for that tag without being kept: what the desk last told everybody watching is
        // still what its next refresh compares against, so they are told too.
        if (!entity.label().phase().equals("signup")) {
            Events.removeForTesting(state);
            helper.fail("sending one client a label changed what the desk thinks everybody was told: " + entity.label());
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

    /**
     * A comparator beside a desk is lit while the round has had time called, and dark before and after:
     * the round's own clock calls it, the desk's tick notices, and the comparator is told.
     */
    @GameTest(template = "tables", timeoutTicks = 120)
    public static void aDeskSignalsWhenTimeIsCalled(GameTestHelper helper) {
        BlockPos table = EventBoardGameTest.place(helper, 4, 2, 4);
        EventState state = EventBoardGameTest.fourPlayerEvent(helper, table);
        BlockPos desk = placeDesk(helper);
        deskOf(helper, desk).runs(state.tournament.id());
        BlockPos comparator = desk.east();
        helper.getLevel().setBlock(comparator, net.minecraft.world.level.block.Blocks.COMPARATOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ComparatorBlock.FACING, Direction.WEST), 3);
        if (helper.getLevel().getBlockState(desk).getAnalogOutputSignal(helper.getLevel(), desk) != 0) {
            Events.removeForTesting(state);
            helper.fail("a desk signals before time has been called");
            return;
        }
        // The round's whole clock, run through: time is called the way the event calls it.
        Events.runClockForTesting(helper.getLevel().getServer(), state,
                state.tournament.settings().roundMinutes() * Events.MINUTE_MILLIS / 50 + 20);
        helper.runAfterDelay(25, () -> {
            try {
                if (!state.tournament.currentRound().orElseThrow().timeCalled()) {
                    helper.fail("the round's clock did not call time");
                    return;
                }
                int strength = helper.getLevel().getBlockState(desk).getAnalogOutputSignal(helper.getLevel(), desk);
                int reading = helper.getLevel().getBlockEntity(comparator)
                        instanceof net.minecraft.world.level.block.entity.ComparatorBlockEntity read ? read.getOutputSignal() : -1;
                if (strength != 15 || reading != 15) {
                    helper.fail("with time called the desk gives " + strength + " and the comparator reads " + reading);
                    return;
                }
                if (!deskOf(helper, desk).label().phase().equals("time")) {
                    helper.fail("with time called the desk is labeled " + deskOf(helper, desk).label());
                    return;
                }
                helper.succeed();
            } finally {
                Events.removeForTesting(state);
            }
        });
    }

    /**
     * A comparator that kept full strength through its chunk being unloaded is set right when the
     * desk loads again, though whether time is called did not change while the desk was away.
     */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void aComparatorLeftLitIsPutOutWhenTheDeskLoads(GameTestHelper helper) {
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        EventState state = hostedBy(helper, host, "Friday Night");
        BlockPos desk = helper.absolutePos(DESK);
        BlockPos comparator = desk.east();
        // Both as a chunk loads them, with no block updates between them: the comparator as it was
        // saved while time was called, and the desk with only its tournament in its tag.
        helper.getLevel().setBlock(comparator, net.minecraft.world.level.block.Blocks.COMPARATOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ComparatorBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.ComparatorBlock.POWERED, true), 2);
        if (helper.getLevel().getBlockEntity(comparator) instanceof net.minecraft.world.level.block.entity.ComparatorBlockEntity read) {
            read.setOutputSignal(15);
        }
        helper.getLevel().setBlock(desk, GatheringContent.SCOREKEEPERS_DESK.get().defaultBlockState()
                .setValue(dev.gathering.block.ScorekeepersDeskBlock.FACING, Direction.SOUTH), 2);
        CompoundTag saved = new CompoundTag();
        saved.putUUID("event", state.tournament.id());
        deskOf(helper, desk).loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.runAfterDelay(30, () -> {
            Events.removeForTesting(state);
            int reading = helper.getLevel().getBlockEntity(comparator)
                    instanceof net.minecraft.world.level.block.entity.ComparatorBlockEntity read ? read.getOutputSignal() : -1;
            if (reading != 0) {
                helper.fail("a comparator left at " + reading + " by a round long over still reads it");
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
                // It used to ask for useShapeForLightOcclusion, which only means anything to a block that
                // occludes at all. The desk no longer does - that is what stops its neighbors' faces being
                // culled against it - so the same claim, made the stronger way.
                || state.canOcclude()) {
            helper.fail("the desk is a full cube where it shows a lectern");
            return;
        }
        helper.succeed();
    }

    /**
     * A desk carried somewhere - written down, loaded where it went, and the old one cleared in the
     * same tick, as Sable carries one onto a ship - takes signing up with it. A copy whose original
     * stays moves nothing.
     */
    @GameTest(template = "empty")
    public static void aCarriedDeskTakesSigningUpWithIt(GameTestHelper helper) {
        BlockPos desk = placeDesk(helper);
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        EventState state = hostedBy(helper, host, "Friday Night");
        try {
            use(helper, host);
            // A copy that stays beside its original: signing up stays where it was.
            CompoundTag saved = deskOf(helper, desk).saveWithFullMetadata(helper.getLevel().registryAccess());
            BlockPos copy = desk.offset(4, 0, 0);
            helper.getLevel().setBlock(copy, GatheringContent.SCOREKEEPERS_DESK.get().defaultBlockState(), 3);
            deskOf(helper, copy).loadWithComponents(saved, helper.getLevel().registryAccess());
            if (!desk.equals(state.registrationPoint)) {
                helper.fail("copying a desk moved signing up to " + state.registrationPoint);
                return;
            }
            // Carried: loaded where it went and the old one cleared, in one tick.
            saved = deskOf(helper, desk).saveWithFullMetadata(helper.getLevel().registryAccess());
            BlockPos there = desk.offset(0, 0, 4);
            helper.getLevel().setBlock(there, GatheringContent.SCOREKEEPERS_DESK.get().defaultBlockState(), 3);
            deskOf(helper, there).loadWithComponents(saved, helper.getLevel().registryAccess());
            helper.getLevel().removeBlock(desk, false);
            if (!there.equals(state.registrationPoint)) {
                helper.fail("a desk carried elsewhere left signing up at " + state.registrationPoint);
                return;
            }
        } finally {
            Events.removeForTesting(state);
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
