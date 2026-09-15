package dev.gathering.neoforge.compat.create.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TablePart;
import dev.gathering.item.GatheringContent;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A table, taught in one scene: its size and seats, tables joining, sitting down with a deck,
 * choosing a format, its number in a tournament, and a Display Link on it.
 * <p>On Create's Display Link schematic, as the desk's scene is. The words are the lang file's
 * ({@code gathering.ponder.table.*}); the pack scene checks they agree with those given here.
 * <p>No game is drawn on these tables: what a client knows about a table is known by its position
 * in the world being played, and a scene is not that world.
 */
final class TablePonderScene {

    private TablePonderScene() {
    }

    static void table(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("table", "Playing at a Table");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        // One table: three blocks by three is as much of Create's five-block plate as a table can take
        // and still leave room for a chair at either end of it. Pushing tables together into a line is
        // in the guide rather than here, because two of them do not fit on this plate.
        BlockPos first = util.grid().at(1, 1, 0);
        BlockPos middle = util.grid().at(2, 1, 1);
        Selection firstTable = util.select().fromTo(1, 1, 0, 3, 1, 2);
        BlockPos westChair = util.grid().at(0, 1, 1);
        BlockPos eastChair = util.grid().at(4, 1, 1);
        Selection chairs = util.select().position(westChair).add(util.select().position(eastChair));
        BlockPos linkPos = util.grid().at(4, 1, 2);
        Selection link = util.select().position(linkPos);
        BlockPos board = util.grid().at(3, 2, 3);
        Selection fullBoard = util.select().fromTo(3, 2, 3, 1, 1, 3);
        Selection largeCog = util.select().position(3, 0, 5);
        Selection smallCogs = util.select().fromTo(4, 1, 5, 4, 1, 3);

        BlockState table = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            scene.world().setBlock(part.offsetFrom(first), table.setValue(TableBlock.PART, part), false);
        }
        // Played across its east and west edges, the way its first sitter would have turned it.
        scene.world().modifyBlockEntity(first, TableBlockEntity.class, entity -> entity.setTurned(true));
        BlockState chair = GatheringContent.CHAIR.get().defaultBlockState();
        scene.world().setBlock(westChair, chair.setValue(dev.gathering.block.ChairBlock.FACING, Direction.EAST), false);
        scene.world().setBlock(eastChair, chair.setValue(dev.gathering.block.ChairBlock.FACING, Direction.WEST), false);
        scene.world().setBlock(linkPos, AllBlocks.DISPLAY_LINK.getDefaultState()
                .setValue(DisplayLinkBlock.FACING, Direction.EAST), false);
        scene.idle(15);

        scene.world().showSection(firstTable, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("A table is three blocks by three, and seats two")
                .pointAt(util.vector().topOf(middle))
                .placeNearTarget();
        scene.idle(80);

        scene.world().showSection(chairs, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(westChair), Pointing.DOWN, 50).rightClick();
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Sit in a chair at the middle of an edge to play; the other player sits opposite")
                .pointAt(util.vector().topOf(westChair))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(middle), Pointing.DOWN, 50).rightClick()
                .withItem(new ItemStack(GatheringContent.DECK.get()));
        scene.idle(10);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Right-click the table from your chair to choose a game, then with a deck to put it down")
                .pointAt(util.vector().topOf(middle))
                .placeNearTarget();
        scene.idle(90);

        scene.world().modifyBlockEntity(first, TableBlockEntity.class, entity -> entity.setEventLabel(1, "Alice - Chris", 0));
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("In a tournament, each table shows its number and who is playing at it")
                .pointAt(util.vector().topOf(middle))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(largeCog, Direction.UP);
        scene.world().showSection(smallCogs, Direction.WEST);
        scene.idle(5);
        scene.world().showSection(fullBoard, Direction.NORTH);
        scene.idle(20);
        scene.world().showSection(link, Direction.NORTH);
        scene.idle(20);
        scene.effects().indicateSuccess(linkPos);
        // A part a line, as the table's match is written on anything this narrow.
        scene.world().setDisplayBoardText(board, 0, Component.literal("1. Alice"));
        scene.world().setDisplayBoardText(board, 1, Component.literal("vs Chris"));
        scene.world().flashDisplayLink(linkPos);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("A Display Link on a table shows the match at it, or the life totals of its game")
                .pointAt(util.vector().centerOf(linkPos))
                .placeNearTarget();
        scene.idle(100);
    }
}
