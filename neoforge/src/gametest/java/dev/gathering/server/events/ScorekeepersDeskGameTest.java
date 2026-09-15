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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Scorekeeper's Desk: a host using it makes it their tournament's desk and the place signing up
 * happens; anybody else using it leaves it alone; sneaking takes it over; breaking it lets signing
 * up go anywhere again. Each through the block, the way a player's click reaches it.
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
            helper.useBlock(DESK, host);
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
            // Another host's plain click is a look, not a takeover.
            helper.useBlock(DESK, passerBy);
            if (!runs(helper, desk, state) || theirs.registrationPoint != null) {
                helper.fail("another host's click without sneaking took the desk");
                return;
            }
            passerBy.setShiftKeyDown(true);
            helper.useBlock(DESK, passerBy);
            if (!runs(helper, desk, theirs) || !desk.equals(theirs.registrationPoint)) {
                helper.fail("sneaking did not take the desk over");
                return;
            }
            if (state.registrationPoint != null) {
                helper.fail("the tournament that lost its desk still signs players up at it");
                return;
            }
        } finally {
            Events.removeForTesting(state);
            Events.removeForTesting(theirs);
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
            helper.useBlock(DESK, player);
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
            helper.useBlock(DESK, first);
            Events.setForTesting(over, over.tournament.cancel());
            helper.useBlock(DESK, second);
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
            helper.useBlock(DESK, host);
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
        Tournament tournament = Tournament.create(UUID.randomUUID(), name, host.getUUID(),
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        EventState state = Events.stateForTesting(tournament, helper.getLevel(), List.of());
        Events.putForTesting(state);
        return state;
    }
}
