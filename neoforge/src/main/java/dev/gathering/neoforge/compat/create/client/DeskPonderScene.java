package dev.gathering.neoforge.compat.create.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.gathering.block.ScorekeepersDeskBlock;
import dev.gathering.block.ScorekeepersDeskBlockEntity;
import dev.gathering.item.GatheringContent;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * The Scorekeeper's Desk, taught in one scene: what it is for, what using it does and who may, and
 * a Display Link on it showing the tournament on a board, and a Clipboard taking the round down.
 * <p>The words are the lang file's ({@code gathering.ponder.scorekeepers_desk.*}); those given here
 * are what Ponder records them as, and the pack scene checks the two agree.
 */
final class DeskPonderScene {

    private DeskPonderScene() {
    }

    static void deskAndBoard(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("scorekeepers_desk", "Running a Tournament from a Scorekeeper's Desk");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        // Create's Display Link schematic, with its depot made a desk.
        BlockPos deskPos = util.grid().at(3, 1, 1);
        Selection desk = util.select().position(deskPos);
        BlockPos linkPos = util.grid().at(2, 1, 1);
        Selection link = util.select().position(linkPos);
        BlockPos board = util.grid().at(3, 2, 3);
        Selection fullBoard = util.select().fromTo(3, 2, 3, 1, 1, 3);
        Selection largeCog = util.select().position(3, 0, 5);
        Selection smallCogs = util.select().fromTo(4, 1, 5, 4, 1, 3);

        scene.world().setBlock(deskPos, GatheringContent.SCOREKEEPERS_DESK.get().defaultBlockState()
                .setValue(ScorekeepersDeskBlock.FACING, Direction.NORTH), false);
        scene.idle(15);
        scene.world().showSection(desk, Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("A Scorekeeper's Desk is where a tournament is run from")
                .pointAt(util.vector().blockSurface(deskPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(deskPos), Pointing.DOWN, 40).rightClick();
        scene.idle(10);
        scene.world().modifyBlockEntity(deskPos, ScorekeepersDeskBlockEntity.class, entity -> entity.showLabel(
                new ScorekeepersDeskBlockEntity.Label("Friday Night", "signup", 0, 0, "")));
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Use it to host one, played at the tables near it: players sign up at the desk, and its name floats over it")
                .pointAt(util.vector().topOf(deskPos))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Anybody else using it is shown the tournament. Another host takes it over by using it twice")
                .pointAt(util.vector().topOf(deskPos))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(largeCog, Direction.UP);
        scene.world().showSection(smallCogs, Direction.WEST);
        scene.idle(5);
        scene.world().showSection(fullBoard, Direction.NORTH);
        scene.idle(20);
        scene.world().showSection(link, Direction.EAST);
        scene.idle(20);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("With a Display Link on the desk, a board shows its tournament")
                .pointAt(util.vector().centerOf(linkPos))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(linkPos), Pointing.DOWN, 60).rightClick();
        scene.idle(20);
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Choose what it shows: the standings, the pairings, the round and its clock, the final places, the prizes, or who has signed up")
                .pointAt(util.vector().centerOf(linkPos))
                .placeNearTarget();
        scene.idle(60);
        scene.effects().indicateSuccess(linkPos);
        scene.world().modifyBlockEntity(deskPos, ScorekeepersDeskBlockEntity.class, entity -> entity.showLabel(
                new ScorekeepersDeskBlockEntity.Label("Friday Night", "swiss", 3, 4, "")));
        scene.world().setDisplayBoardText(board, 0, Component.literal("1. Alice  9"));
        scene.world().setDisplayBoardText(board, 1, Component.literal("2. Bob  6"));
        scene.world().flashDisplayLink(linkPos);
        scene.idle(60);

        // As short as a three-wide board wants them.
        scene.world().setDisplayBoardText(board, 0, Component.literal("1 Alice-Dana"));
        scene.world().setDisplayBoardText(board, 1, Component.literal("2 Bob-Chris"));
        scene.world().flashDisplayLink(linkPos);
        scene.overlay().showText(80)
                .text("A table offers its own match and its game's life totals the same way")
                .pointAt(util.vector().blockSurface(board, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(deskPos), Pointing.DOWN, 50).rightClick()
                .withItem(AllBlocks.CLIPBOARD.asStack());
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("A Clipboard used on the desk takes down the round's pairings and the standings")
                .pointAt(util.vector().topOf(deskPos))
                .placeNearTarget();
        scene.idle(90);
    }
}
