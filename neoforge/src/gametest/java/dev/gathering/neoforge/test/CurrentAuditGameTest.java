package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.*;
import dev.gathering.item.GatheringContent;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.*;
import dev.gathering.core.game.event.GameEvent;
import dev.gathering.core.game.visibility.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Audit-only adversarial guards. Expected to fail until the reported issues are fixed. */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CurrentAuditGameTest {
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }
    private static BlockPos chair(GameTestHelper h, BlockPos at) {
        h.getLevel().setBlock(at, GatheringContent.CHAIR.get().defaultBlockState().setValue(ChairBlock.FACING, Direction.SOUTH), 3);
        return at;
    }
    @GameTest(template="tables", batch="current_audit")
    public static void rejectedMountDoesNotClaimTableSeat(GameTestHelper h) {
        BlockPos table = TestTables.place(h,1,2,2);
        BlockPos at = chair(h,table.offset(1,0,-1));
        ServerPlayer p = player(h);
        Consumer<EntityMountEvent> deny = event -> {
            if (event.getEntityMounting() == p && event.isMounting()) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(deny);
        try { Chairs.sit(p,at,h.getLevel().getBlockState(at)); }
        finally { NeoForge.EVENT_BUS.unregister(deny); }
        boolean riding = p.getVehicle() != null;
        boolean claimed = TableSeats.seatOf(h.getLevel(),table,p.getUUID()).isPresent();
        System.out.println("[current-audit] cancelled mount riding="+riding+" claimed="+claimed);
        if (riding) { h.fail("fixture: mounting was not refused"); return; }
        if (claimed) { h.fail("Rejected mount still claimed the table seat"); return; }
        h.succeed();
    }
    @GameTest(template="tables", batch="current_audit")
    public static void dismountDoesNotChooseSolidWall(GameTestHelper h) {
        BlockPos at = chair(h,h.absolutePos(new BlockPos(3,2,3)));
        ServerPlayer p = player(h);
        Chairs.sit(p,at,h.getLevel().getBlockState(at));
        if (!(p.getVehicle() instanceof ChairSeat seat)) { h.fail("fixture: no chair mount"); return; }
        BlockPos behind = at.north();
        h.getLevel().setBlock(behind,Blocks.STONE.defaultBlockState(),3);
        h.getLevel().setBlock(behind.above(),Blocks.STONE.defaultBlockState(),3);
        Vec3 destination = seat.getDismountLocationForPassenger(p);
        boolean free = h.getLevel().noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(destination));
        System.out.println("[current-audit] dismount="+destination+" collisionFree="+free);
        if (!free) { h.fail("Chair dismount puts the player's body inside a solid wall despite open space beside it"); return; }
        h.succeed();
    }
    @GameTest(template="tables", batch="current_audit")
    public static void breakingOccupiedChairCannotTakeOverHiddenHand(GameTestHelper h) {
        BlockPos table = TestTables.place(h,1,2,2);
        BlockPos at = chair(h,table.offset(1,0,-1));
        ServerPlayer owner=player(h), attacker=player(h);
        Chairs.sit(owner,at,h.getLevel().getBlockState(at));
        if (TableSessions.start(h.getLevel(),table,TableSessions.defaultRules()) != TableSessions.Outcome.STARTED) {
            h.fail("fixture: session did not start"); return;
        }
        GameSession session = TableSessions.sessionAt(h.getLevel(),table).orElseThrow();
        SeatId original = TableSessions.seatIdOf(h.getLevel(),table,owner.getUUID()).orElseThrow();
        session.submit(new GameEvent.DeckLoaded(original,List.of(CardIdentity.ofPrinting(UUID.fromString("bbbbbbbb-2222-4222-8222-222222222222"),false)),List.of()));
        session.submit(new GameEvent.CardsDrawn(original,original,1));
        if (session.state().contents(original,Zone.HAND).size()!=1) { h.fail("fixture: hand not loaded"); return; }
        attacker.setPos(at.getX()+0.5,at.getY(),at.getZ()-0.5);
        boolean destroyed = attacker.gameMode.destroyBlock(at);
        if (!destroyed) { h.succeed(); return; }
        chair(h,at);
        Chairs.sit(attacker,at,h.getLevel().getBlockState(at));
        var taken = TableSessions.seatIdOf(h.getLevel(),table,attacker.getUUID());
        long exposed = taken.map(seat -> VisibilityRules.viewFor(session.state(),new Viewer.Seated(seat)).seat(seat).zone(Zone.HAND).cards().stream().filter(CardView.Visible.class::isInstance).count()).orElse(0L);
        System.out.println("[current-audit] brokenChair="+destroyed+" takeover="+taken+" visibleHandCards="+exposed);
        if (exposed>0) { h.fail("Breaking and replacing an occupied chair lets another player take its seat and see the original hidden hand"); return; }
        h.succeed();
    }
}
