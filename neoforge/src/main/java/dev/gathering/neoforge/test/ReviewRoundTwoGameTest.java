package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.*;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.item.*;
import dev.gathering.network.*;
import dev.gathering.server.*;
import dev.gathering.service.CardDataService;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.*;

/** Independent audit-only tests. Source under review is unchanged. */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReviewRoundTwoGameTest {
    @GameTest(template="empty")
    public static void delayedCommitMustNotTakeADeckMovedIntoStorage(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        var state=GatheringContent.TABLE.get().defaultBlockState();
        for(var part:TablePart.values()) h.getLevel().setBlock(part.offsetFrom(origin),state.setValue(TableBlock.PART,part),3);
        var seats=TableClusters.at(h.getLevel(),origin).seats();
        for(int i=0;i<2;i++) {
            var a=seats.get(i);
            TableSeats.take(h.getLevel(),origin,a.cell(),a.side(),i==0?player.getUUID():UUID.randomUUID());
        }
        TableSessions.start(h.getLevel(),origin,new MatchRules(FormatPresets.COMMANDER,1));
        var card=CardComponent.of(CardIdentity.ofPrinting(UUID.randomUUID()));
        var stack=DeckItem.of(new DeckComponent("Delayed", "",Optional.empty(),List.of(card),List.of(),List.of()));
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var fetched=new CompletableFuture<Void>();
        // Looked up by name rather than by signature: the fix this test is the guard for
        // added the two arguments that say where the deck came from, and pinning the old
        // five-argument shape here would fail as a missing method instead of as a behavior.
        java.lang.reflect.Method resume=null;
        for(var candidate:TableBlock.class.getDeclaredMethods())
            if(candidate.getName().equals("waitAndTryAgain")) resume=candidate;
        if(resume==null) throw new AssertionError("no waitAndTryAgain to exercise");
        resume.setAccessible(true);
        Object[] args=new Object[resume.getParameterCount()];
        for(int i=0;i<args.length;i++) {
            Class<?> want=resume.getParameterTypes()[i];
            if(want==Level.class) args[i]=h.getLevel();
            else if(want==BlockPos.class) args[i]=origin;
            else if(want==Player.class) args[i]=player;
            else if(want==ItemStack.class) args[i]=stack;
            else if(want==InteractionHand.class) args[i]=InteractionHand.MAIN_HAND;
            else if(want==CompletableFuture.class) args[i]=fetched;
            else if(want.isEnum()) {
                // The deck in this test came out of the player's own hand, which is the case
                // with something to check: a loaner was never in an inventory to leave.
                for(Object constant:want.getEnumConstants())
                    if(constant.toString().equals("THEIR_HAND")) args[i]=constant;
                if(args[i]==null) throw new AssertionError("no THEIR_HAND in "+want);
            }
            else throw new AssertionError("unexpected parameter "+want);
        }
        resume.invoke(null,args);
        // Simulate moving the actual stack out of the player's inventory before lookup completes.
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        var storage=new SimpleContainer(1);
        storage.setItem(0,stack);
        fetched.complete(null);
        h.runAfterDelay(1,()-> {
            if(storage.getItem(0).isEmpty()) h.fail("ROUND2: delayed commit consumed a deck after it left the player's inventory");
            else h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void twoSearchesInOneTickMustNotOverflowTheStack(GameTestHelper h) {
        var player=h.makeMockServerPlayerInLevel();
        CollectionView.forget(player.getUUID());
        boolean overflow=false;
        try {
            for(int i=0;i<2;i++) CollectionView.search(player,BlockPos.ZERO,CollectionQuery.EVERYTHING,false,0,10,false,i+1);
        } catch(StackOverflowError found) {overflow=true;}
        finally {CollectionView.forget(player.getUUID());}
        if(overflow) h.fail("ROUND2: collection throttle recursively executes until StackOverflowError instead of waiting for another tick");
        else h.succeed();
    }

    /**
     * One debt is one item, however many times delivery is called and however badly the
     * ledger behaves.
     * <p>The audit that supplied this test asserted exactly one pack while the ledger could
     * not be rewritten. That is not reachable, and asserting it would be asserting the wrong
     * thing: an acknowledgment that cannot be written down cannot be made durable by any
     * ordering, so the only two behaviors available are to pay and risk paying again after a
     * restart, or to hold the debt and pay it when the write works. This mod holds it - see
     * {@link Owed#deliver} - so the count under a blocked ledger is zero, not one.
     * <p>What the audit was really testing is the part this checks instead, and checks
     * harder: the debt is never paid twice, and holding it is not losing it. Delivery is
     * called twice against a blocked ledger and must hand over nothing; the block is then
     * lifted and delivery called twice more, and exactly one pack must arrive in total.
     */
    @GameTest(template="empty")
    public static void owedDeliveryMustNotRepeatAfterARewriteFailure(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        UUID who=player.getUUID();
        Owed.forget(who);
        Path folder=ServerRun.inSave("owed").orElseThrow();
        Path list=folder.resolve(who+".txt");
        Path blocked=folder.resolve(who+".txt.writing");
        try {
            if(!Owed.aPack(who,"dmu","draft")) throw new AssertionError("fixture write failed");
            Files.writeString(list,Files.readString(list)+"\nfuture-kind unknown\n");
            Files.createDirectory(blocked);
            Files.writeString(blocked.resolve("keep-blocked"),"fault injection");
            Owed.deliver(player);
            Owed.deliver(player);
            if(packsHeldBy(player)!=0)
                h.fail("ROUND2: owed packs were handed over while the ledger could not be"
                        +" shortened, so a restart would hand them over again");
            // The debt is held, not lost: with the ledger writable again it pays out, once.
            Files.deleteIfExists(blocked.resolve("keep-blocked"));
            Files.deleteIfExists(blocked);
            Owed.deliver(player);
            Owed.deliver(player);
            int packs=packsHeldBy(player);
            if(packs!=1) h.fail("ROUND2: one owed pack delivered "+packs+" times");
            else h.succeed();
        } finally {
            Files.deleteIfExists(blocked.resolve("keep-blocked"));
            Files.deleteIfExists(blocked);
            Owed.forget(who);
        }
    }

    private static int packsHeldBy(ServerPlayer player) {
        int packs=0;
        for(var stack:player.getInventory().items) if(stack.getItem() instanceof PackItem) packs+=stack.getCount();
        return packs;
    }

    /** A stopped-progress worker lets us measure admitted work without contacting Scryfall. */
    static final class HeldExecutor extends AbstractExecutorService {
        final List<Runnable> queued=new ArrayList<>();
        boolean stopped;
        public void execute(Runnable r){queued.add(r);}
        public void shutdown(){stopped=true;}
        public List<Runnable> shutdownNow(){stopped=true;return List.copyOf(queued);}
        public boolean isShutdown(){return stopped;}
        public boolean isTerminated(){return stopped;}
        public boolean awaitTermination(long n,TimeUnit u){return stopped;}
    }

    @GameTest(template="empty")
    public static void reconnectMustNotResetTheBudgetOfWorkStillQueued(GameTestHelper h) throws Exception {
        var player=h.makeMockServerPlayerInLevel();
        var ctor=CardDataService.class.getDeclaredConstructor(Path.class,String.class);
        ctor.setAccessible(true);
        var service=(CardDataService)ctor.newInstance(Files.createTempDirectory("gathering-review-queue-"),"audit/no-network");
        var field=CardDataService.class.getDeclaredField("executor");
        field.setAccessible(true);
        ((ExecutorService)field.get(service)).shutdownNow();
        var executor=new HeldExecutor();
        field.set(service,executor);
        try {
            CardMetadataRequests.forget(player.getUUID());
            for(int connection=0;connection<6;connection++) {
                var ids=new ArrayList<UUID>();
                for(int i=0;i<128;i++) ids.add(UUID.randomUUID());
                CardMetadataRequests.handle(player,service,new RequestCardMetadataPayload(ids));
                // Same cleanup invoked by PlayerGone when this UUID disconnects.
                CardMetadataRequests.forget(player.getUUID());
            }
            int admitted=executor.queued.size()*128;
            if(admitted>512) h.fail("ROUND2: "+admitted+" printing lookups admitted with zero completed against a claimed global cap of 512");
            else h.succeed();
        } finally {CardMetadataRequests.forget(player.getUUID());service.close();}
    }
}
