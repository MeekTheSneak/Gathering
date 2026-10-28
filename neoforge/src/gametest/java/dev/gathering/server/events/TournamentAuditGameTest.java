package dev.gathering.server.events;

import dev.gathering.Gathering;
import dev.gathering.block.*;
import dev.gathering.core.tournament.*;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.item.*;
import dev.gathering.network.*;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.server.*;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TournamentAuditGameTest {
    record Fixture(EventState state, ServerPlayer host, ServerPlayer other, BlockPos table) { }
    static Fixture fixture(GameTestHelper h, EventSettings.Kind kind, boolean playing) {
        BlockPos at = h.absolutePos(new BlockPos(1,2,1));
        for (TablePart part : TablePart.values()) {
            h.getLevel().setBlock(part.offsetFrom(at), GatheringContent.TABLE.get().defaultBlockState()
                    .setValue(TableBlock.PART,part),3);
        }
        ServerPlayer host=h.makeMockServerPlayerInLevel(), other=h.makeMockServerPlayerInLevel();
        host.setGameMode(GameType.SURVIVAL); other.setGameMode(GameType.SURVIVAL);
        host.setPos(at.getX()+0.5,at.getY(),at.getZ()+0.5);
        other.setPos(at.getX()+0.5,at.getY(),at.getZ()+0.5);
        EventSettings settings=new EventSettings(kind,"modern",null,3,50,30,5,2,0,
                EventSettings.DeckRegistration.OFF,false);
        Tournament t=Tournament.create(UUID.randomUUID(),"Audit",host.getUUID(),settings)
                .register(Entrant.registering(host.getUUID(),"Host",1600))
                .register(Entrant.registering(other.getUUID(),"Other",1500)).beginPreparing();
        if(playing) t=t.startSwiss();
        EventState state=Events.stateForTesting(t,h.getLevel(),List.of(at));
        Events.putForTesting(state);
        if(playing) Events.seatRoundForTesting(h.getLevel().getServer(),state);
        return new Fixture(state,host,other,at);
    }
    static void clean(Fixture f) {
        ServerTicks.forget("event-next-"+f.state.tournament.id());
        Events.removeForTesting(f.state);
    }
    @GameTest(template="tables")
    public static void cancelingAnOldEventMustNotEndANewMatch(GameTestHelper h) {
        Fixture f=fixture(h,EventSettings.Kind.CONSTRUCTED,true);
        EventState old=Events.stateForTesting(Tournament.create(UUID.randomUUID(),"Old",f.host.getUUID(),
                f.state.tournament.settings()).cancel(),h.getLevel(),List.of(f.table));
        Events.putForTesting(old);
        try {
            if(!TableSessions.hasSession(h.getLevel(),f.table)) throw new AssertionError("fixture missing live game");
            EventViews.act(f.host,EventActionPayload.of(old.tournament.id(),EventActionPayload.Action.CANCEL));
            if(!TableSessions.hasSession(h.getLevel(),f.table)) h.fail("CANCEL on an old event destroyed a different active event's game");
            h.succeed();
        } finally { Events.removeForTesting(old); clean(f); }
    }
    @GameTest(template="tables",timeoutTicks=400)
    public static void withdrawingTheLastMatchMustAdvanceTheRound(GameTestHelper h) {
        Fixture f=fixture(h,EventSettings.Kind.CONSTRUCTED,true);
        EventViews.act(f.other,EventActionPayload.of(f.state.tournament.id(),EventActionPayload.Action.WITHDRAW));
        if(!f.state.tournament.currentRound().orElseThrow().isComplete()) {
            clean(f); throw new AssertionError("fixture withdrawal did not complete the round");
        }
        h.runAfterDelay(340,()-> {
            try {
                if(!f.state.tournament.isOver() && f.state.tournament.currentRound().orElseThrow().number()==1)
                    h.fail("WITHDRAW confirmed the last pairing but never scheduled advancement");
                h.succeed();
            } finally {clean(f);}
        });
    }
    @GameTest(template="tables",timeoutTicks=400)
    public static void completedRoundMustResumeAfterReload(GameTestHelper h) throws Exception {
        Fixture f=fixture(h,EventSettings.Kind.CONSTRUCTED,true);
        // A real confirmed result is saved before its 15-second callback; reloading only the
        // saved event models a restart, which discards ServerTicks' transient callback.
        Events.settle(f.host,f.state.tournament.id(),1,new MatchResult(2,0,0));
        EventState loaded=Events.roundTripForTesting(f.state);
        clean(f);
        Events.putForTesting(loaded);
        h.runAfterDelay(340,()-> {
            try {
                if(!loaded.tournament.isOver() && loaded.tournament.currentRound().orElseThrow().number()==1)
                    h.fail("Reloaded complete round never resumed; only the lost transient callback advances it");
                h.succeed();
            } finally { Events.removeForTesting(loaded); ServerTicks.forget("event-next-"+loaded.tournament.id()); }
        });
    }
    @GameTest(template="tables")
    public static void oldPoolCannotReadyANewUnopenedEvent(GameTestHelper h) {
        Fixture f=fixture(h,EventSettings.Kind.SEALED,false);
        try {
            CardComponent card=CardComponent.of(CardIdentity.ofPrinting(UUID.randomUUID()));
            List<CardComponent> cards=Collections.nCopies(40,card);
            ItemStack stack=DeckItem.of(new DeckComponent("Old pool","",Optional.of(f.host.getUUID()),cards,List.of(),List.of()));
            stack.set(GatheringComponents.POOL.get(),new DraftedPool(cards,f.table.toShortString()));
            f.host.getInventory().setItem(f.host.getInventory().selected,stack);
            EventViews.act(f.host,EventActionPayload.of(f.state.tournament.id(),EventActionPayload.Action.READY));
            if(f.state.tournament.ready().contains(f.host.getUUID()))
                h.fail("A coordinate-matching old pool was accepted although this event never opened packs");
            h.succeed();
        } finally {clean(f);}
    }
    @GameTest(template="tables")
    public static void startNowCannotSkipAnUnfinishedPod(GameTestHelper h) {
        Fixture f=fixture(h,EventSettings.Kind.SEALED,false);
        try {
            TableBlock.entityAt(h.getLevel(),f.table).orElseThrow().setSignup(
                    PodSignup.open(f.host.getUUID(),f.state.tournament.settings().pod()));
            f.state.podOpened=true;
            EventViews.act(f.host,EventActionPayload.of(f.state.tournament.id(),EventActionPayload.Action.START_NOW));
            if(f.state.tournament.phase()==Tournament.Phase.SWISS && !TableSessions.hasSession(h.getLevel(),f.table))
                h.fail("START_NOW advanced to Swiss while the signup prevents its game from starting");
            h.succeed();
        } finally {clean(f);}
    }
    @GameTest(template="tables")
    public static void failedPrizeSaveMustNotConsumeTheItem(GameTestHelper h) throws Exception {
        Fixture f=fixture(h,EventSettings.Kind.CONSTRUCTED,false);
        Path folder=ServerRun.inSave("gathering-events").orElseThrow();
        Files.createDirectories(folder);
        Path target=folder.resolve(f.state.tournament.id()+".dat");
        Path tmp=folder.resolve(f.state.tournament.id()+".dat.tmp");
        // Failure injection is local to this event. A nonempty directory at the target makes
        // atomic replacement fail without changing another test's save or server lifecycle.
        Files.createDirectory(target);
        Files.writeString(target.resolve("blocker"),"audit");
        try {
            f.host.getInventory().setItem(f.host.getInventory().selected,new ItemStack(Items.DIAMOND,3));
            EventViews.act(f.host,new EventActionPayload(f.state.tournament.id(),EventActionPayload.Action.ADD_PRIZE,
                    1,0,0,0,EventActionPayload.NONE,BlockPos.ZERO));
            if(f.host.getMainHandItem().isEmpty()) h.fail("Prize deposit consumed diamonds despite a failed durable save");
            h.succeed();
        } finally {Files.deleteIfExists(target.resolve("blocker"));Files.deleteIfExists(target);Files.deleteIfExists(tmp);clean(f);}
    }
    @GameTest(template="tables")
    public static void malformedReportMustNotPoisonOutboundView(GameTestHelper h) {
        Fixture f=fixture(h,EventSettings.Kind.CONSTRUCTED,true);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            EventViews.act(f.host,new EventActionPayload(f.state.tournament.id(),EventActionPayload.Action.REPORT,
                    0,Integer.MAX_VALUE,Integer.MAX_VALUE-1,0,EventActionPayload.NONE,BlockPos.ZERO));
            EventViewPayload view=EventViews.viewFor(h.getLevel().getServer(),f.host.getUUID(),f.state,false);
            try {
                EventViewPayload.STREAM_CODEC.encode(buffer,view);
            } catch(RuntimeException failure) {
                h.fail("Malformed report persisted an unencodable view: "+failure);
                return;
            }
            h.succeed();
        } finally {buffer.release();clean(f);}
    }
    @GameTest(template="tables")
    public static void everyVenueClusterMustSplitBeforePairing(GameTestHelper h) {
        Fixture f=fixture(h,EventSettings.Kind.CONSTRUCTED,false);
        try {
            for(int x : new int[]{1,7,10}) {
                BlockPos at=h.absolutePos(new BlockPos(x,2,1));
                for(TablePart part:TablePart.values()) h.getLevel().setBlock(part.offsetFrom(at),
                        GatheringContent.TABLE.get().defaultBlockState().setValue(TableBlock.PART,part),3);
                f.state.tables.add(at);
            }
            Tournament t=Tournament.create(f.state.tournament.id(),"Two rows",f.host.getUUID(),f.state.tournament.settings());
            for(int i=0;i<8;i++) {
                ServerPlayer player=i==0?f.host:i==1?f.other:h.makeMockServerPlayerInLevel();
                t=t.register(Entrant.registering(player.getUUID(),"P"+i,1600-i));
            }
            f.state.tournament=t;
            EventViews.act(f.host,EventActionPayload.of(t.id(),EventActionPayload.Action.BEGIN));
            BlockPos secondRow=f.state.tables.get(2);
            if(!TableBlock.entityAt(h.getLevel(),secondRow).orElseThrow().playsApart())
                h.fail("Starting a venue split only its first row; second row's two pairings share one session");
            h.succeed();
        } finally {clean(f);}
    }

    @GameTest(template="tables")
    public static void opponentsReportMustUseTheViewersPerspective(GameTestHelper h) {
        Fixture f=fixture(h,EventSettings.Kind.CONSTRUCTED,true);
        try {
            EventViews.act(f.other,new EventActionPayload(f.state.tournament.id(),EventActionPayload.Action.REPORT,
                    0,1,2,0,EventActionPayload.NONE,BlockPos.ZERO));
            String theirs=EventViews.viewFor(h.getLevel().getServer(),f.host.getUUID(),f.state,false).mine().theirReport();
            if(!"2-1".equals(theirs)) h.fail("Opponent reported losing 1-2; winner's view should say 2-1, but says "+theirs);
            h.succeed();
        } finally {clean(f);}
    }

}
