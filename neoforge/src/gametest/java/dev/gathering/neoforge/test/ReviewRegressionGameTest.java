package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.*;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.story.*;
import dev.gathering.core.table.*;
import dev.gathering.item.*;
import dev.gathering.network.DeckEditPayload;
import dev.gathering.server.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** Acceptance tests in a disposable audit checkout; not a repository change. */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReviewRegressionGameTest {
    static CardComponent card() {return CardComponent.of(CardIdentity.ofPrinting(UUID.randomUUID()));}
    static DeckComponent withStory(CardComponent c) {
        return new DeckComponent("Audit", "", Optional.empty(), List.of(c), List.of(), List.of())
            .keeping(c, CardStory.begunWith(new CardStory.Chapter(HowItCame.PULLED,"Audit","","DMU","2026-09-09")));
    }
    @GameTest(template="empty")
    public static void renamingMustPreserveProvenance(GameTestHelper h) {
        var c=card();var d=withStory(c);
        if(d.named("Changed").storyOf(c).isEmpty()) {h.fail("REVIEW G-36: renaming erases the stored card history");return;}
        h.succeed();
    }
    @GameTest(template="empty")
    public static void realTakeMustReturnCardHistory(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();var c=card();
        p.setItemInHand(InteractionHand.MAIN_HAND,DeckItem.of(withStory(c)));
        DeckEdits.handle(p,DeckEditPayload.take(InteractionHand.MAIN_HAND,DeckComponent.Section.MAINBOARD,c));
        boolean found=false;
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            var stack=p.getInventory().getItem(i);
            if(CardItem.cardOf(stack).filter(c::equals).isPresent()) {
                found=true;
                if(CardStories.storyOf(stack).isEmpty()) {h.fail("REVIEW G-36: actual TAKE handler returns the card without its history");return;}
            }
        }
        if(!found) {h.fail("Audit fixture: extracted card was not found");return;}
        h.succeed();
    }
    @GameTest(template="empty")
    public static void owedCapacityMustNotDeletePaidCards(GameTestHelper h) {
        UUID who=UUID.randomUUID();
        try {
            Owed.cards(who,Collections.nCopies(2049,CardIdentity.ofPrinting(UUID.randomUUID())));
            int count=Owed.waitingFor(who);
            if(count!=2049) {h.fail("REVIEW G-04/G-14: 2049 cards owed, only "+count+" retained");return;}
            h.succeed();
        } finally {Owed.forget(who);}
    }
    @GameTest(template="empty")
    public static void replacingAnchorMustEndTheSession(GameTestHelper h) {
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        var state=GatheringContent.TABLE.get().defaultBlockState();
        for(TablePart part:TablePart.values()) h.getLevel().setBlock(part.offsetFrom(origin),state.setValue(TableBlock.PART,part),3);
        var anchors=TableClusters.at(h.getLevel(),origin).seats();
        for(int i=0;i<2;i++) {var a=anchors.get(i);TableSeats.take(h.getLevel(),origin,a.cell(),a.side(),UUID.randomUUID());}
        TableSessions.start(h.getLevel(),origin,new MatchRules(FormatPresets.COMMANDER,1));
        var game=TableSessions.sessionAt(h.getLevel(),origin).orElseThrow();
        h.getLevel().setBlock(origin,Blocks.AIR.defaultBlockState(),3);
        if(!game.state().ended()) {h.fail("REVIEW G-19: forced anchor replacement never ends the live session");return;}
        h.succeed();
    }
}
