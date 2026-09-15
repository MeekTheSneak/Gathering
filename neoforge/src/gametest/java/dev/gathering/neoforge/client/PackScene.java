package dev.gathering.neoforge.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.client.TableCameraView;
import dev.gathering.client.TableScreen;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.match.MatchRules;
import dev.gathering.core.tournament.Entrant;
import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.item.GatheringContent;
import dev.gathering.platform.WorldSpace;
import dev.gathering.server.events.EventState;
import dev.gathering.server.events.Events;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/**
 * The modpack scene: a real client with Create, Create Aeronautics and Sable loaded, photographing what
 * the in-world tests cannot see - a tournament on a Create display board, a table on a Sable structure,
 * and the board on that table from its seat.
 * <p>{@code ./gradlew runPackClient -Ppackscene} with the jars in {@code neoforge/runs/pack/mods}.
 * Pictures land in {@code neoforge/runs/pack/screenshots}; the log says what each step saw.
 */
public final class PackScene {

    private static final String LEVEL = "GatheringPackScene";
    private static final int SETTLE = 40;
    private static final int STUCK_TICKS = 20 * 60 * 4;

    private static int step;
    private static int waited;
    private static int ticks;
    private static final List<String> FAILURES = new ArrayList<>();

    private static BlockPos boardTable;
    private static BlockPos structureTable;
    private static EventState event;

    private PackScene() {
    }

    static void tick(Minecraft client) {
        if (client == null) {
            return;
        }
        if (++ticks > STUCK_TICKS) {
            fail("step " + step + " stopped moving");
            finish(client);
            return;
        }
        if (waited > 0) {
            waited--;
            return;
        }
        client.getToasts().clear();
        switch (step) {
            case 0 -> {
                if (client.getOverlay() == null && client.screen != null) {
                    client.options.pauseOnLostFocus = false;
                    client.setScreen(new TitleScreen());
                    advance(SETTLE);
                }
            }
            case 1 -> {
                shoot(client, "p00-title-with-the-pack");
                makeAWorld(client);
                advance(SETTLE * 4);
            }
            case 2 -> {
                if (client.level == null || client.player == null || client.getSingleplayerServer() == null) {
                    return;
                }
                client.options.pauseOnLostFocus = false;
                dev.gathering.client.ClientSettings.tutorialOffered(true);
                onTheServer(client, PackScene::buildTheBoard);
                advance(SETTLE * 8);
            }
            case 3 -> {
                onTheServer(client, (server, player) -> {
                    BlockPos topLeft = boardTable.offset(-1, 3, -2);
                    if (server.overworld().getBlockEntity(topLeft)
                            instanceof com.simibubi.create.content.trains.display.FlapDisplayBlockEntity flap) {
                        System.out.println("[packscene] the board: controller " + flap.isController + ", running "
                                + flap.isSpeedRequirementFulfilled() + ", speed " + flap.getSpeed() + ", size " + flap.xSize + "x" + flap.ySize);
                    }
                });
                shoot(client, "p01-standings-on-a-display-board");
                onTheServer(client, PackScene::buildTheStructure);
                advance(SETTLE * 3);
            }
            case 4 -> {
                shoot(client, "p02-a-table-on-a-structure");
                onTheServer(client, PackScene::sitAtTheStructure);
                advance(SETTLE * 2);
            }
            case 5 -> {
                if (!(client.screen instanceof TableScreen)) {
                    fail("sitting at the table on the structure opened " + client.screen);
                    finish(client);
                    return;
                }
                shoot(client, "p03-seated-at-a-table-on-a-structure");
                client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
                advance(SETTLE * 2);
            }
            case 6 -> {

                TableCameraView.wanted().ifPresentOrElse(placement -> {
                    Vec3 table = WorldSpace.get().centerInWorld(client.level, structureTable);
                    double across = Math.hypot(placement.x() - table.x, placement.z() - table.z);
                    System.out.println("[packscene] the seated camera is " + across + " blocks across from the table in the world");
                    if (across > 3.0) {
                        fail("the camera over a table on a structure is " + across + " blocks away from it");
                    }
                }, () -> fail("the board on the block has no camera placement"));
                shoot(client, "p04-the-board-on-a-table-on-a-structure");
                advance(SETTLE);
            }
            default -> finish(client);
        }
    }

    /** A flat world, a table with a tournament on it, and a powered display board linked to the table. */
    private static void buildTheBoard(MinecraftServer server, ServerPlayer player) {
        ServerLevel level = server.overworld();
        BlockPos stand = player.blockPosition();
        boardTable = stand.offset(-1, 0, -4);
        place(level, boardTable);
        Tournament tournament = Tournament.create(UUID.randomUUID(), "Friday Night", player.getUUID(),
                EventSettings.usual(EventSettings.Kind.CONSTRUCTED, "modern"));
        String[] names = {"Alice", "Bob", "Chris", "Dana"};
        for (int index = 0; index < names.length; index++) {
            tournament = tournament.register(Entrant.registering(new UUID(11L, index), names[index], 1500 - index));
        }
        tournament = tournament.beginPreparing();
        for (int index = 0; index < names.length; index++) {
            tournament = tournament.markReady(new UUID(11L, index));
        }
        tournament = tournament.startSwiss();
        event = Events.stateForTesting(tournament, level, List.of(boardTable));
        Events.putForTesting(event);

        // A board four wide and three tall standing behind the table, facing the player. Its rows are
        // joined the way a player placing them joins them - the top-left block is the one that shows -
        // and it is turned through a cogwheel beside it, which is how a display board takes rotation.
        BlockPos boardLeft = boardTable.offset(-1, 1, -2);
        // Power first, so the board joins a turning network rather than waiting to be found by one.
        BlockPos cog = boardLeft.offset(-1, 0, 0);
        level.setBlock(cog.north(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, Direction.SOUTH), 3);
        // Faster than the motor's default: a display board only turns its flaps above a speed.
        if (level.getBlockEntity(cog.north()) instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(128);
        }
        level.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock.AXIS, Direction.Axis.Z), 3);
        for (int up = 0; up < 3; up++) {
            for (int across = 0; across < 4; across++) {
                level.setBlock(boardLeft.offset(across, up, 0), AllBlocks.DISPLAY_BOARD.getDefaultState()
                        .setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.SOUTH)
                        .setValue(com.simibubi.create.content.trains.display.FlapDisplayBlock.UP, up < 2)
                        .setValue(com.simibubi.create.content.trains.display.FlapDisplayBlock.DOWN, up > 0), 3);
            }
        }
        BlockPos topLeft = boardLeft.offset(0, 2, 0);
        link(level, boardTable.above(), Direction.UP, topLeft, "the board");

        // And a sign, which needs no power: the same lines, on the simplest thing a link writes to. The
        // link sits on the table's east side facing east, so the table is the block it reads.
        BlockPos sign = boardTable.offset(3, 0, 1);
        level.setBlock(sign, Blocks.OAK_SIGN.defaultBlockState(), 3);
        link(level, boardTable.offset(2, 0, 0), Direction.EAST, sign, "the sign");
        player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 3.5, 180f, 5f);
    }

    private static void link(ServerLevel level, BlockPos link, Direction facing, BlockPos target, String what) {
        level.setBlock(link, AllBlocks.DISPLAY_LINK.getDefaultState().setValue(DisplayLinkBlock.FACING, facing), 3);
        if (level.getBlockEntity(link) instanceof DisplayLinkBlockEntity be) {
            be.target(target);
            be.activeSource = dev.gathering.neoforge.compat.create.CreateCompat.standingsForScenes();
            be.targetLine = 0;
            be.updateGatheredData();
            System.out.println("[packscene] linked " + be.getSourcePosition() + " to " + what + " at " + target
                    + " with " + be.activeSource.getName().getString());
        } else {
            fail("no Display Link was placed for " + what);
        }
    }

    /** A second table on a platform, carried off by Sable into a structure of its own and turned. */
    private static void buildTheStructure(MinecraftServer server, ServerPlayer player) {
        ServerLevel level = server.overworld();
        BlockPos stand = player.blockPosition();
        BlockPos placed = stand.offset(6, 1, -3);
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = -1; x <= 2; x++) {
            for (int z = -1; z <= 2; z++) {
                BlockPos floor = placed.offset(x, -1, z);
                level.setBlock(floor, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                blocks.add(floor.immutable());
            }
        }
        place(level, placed);
        for (TablePart part : TablePart.values()) {
            blocks.add(part.offsetFrom(placed).immutable());
        }
        ServerSubLevel structure = SubLevelAssemblyHelper.assembleBlocks(level, placed, blocks,
                new BoundingBox3i(placed.offset(-1, -1, -1), placed.offset(2, 0, 2)));
        if (structure == null) {
            fail("Sable did not assemble the table into a structure");
            return;
        }
        structure.logicalPose().orientation().set(new Quaterniond().rotateY(Math.toRadians(25)));
        BoundingBox3ic box = structure.getPlot().getBoundingBox();
        for (int x = box.minX(); x <= box.maxX() && structureTable == null; x++) {
            for (int y = box.minY(); y <= box.maxY() && structureTable == null; y++) {
                for (int z = box.minZ(); z <= box.maxZ() && structureTable == null; z++) {
                    if (level.getBlockEntity(new BlockPos(x, y, z)) instanceof TableBlockEntity entity) {
                        structureTable = entity.getBlockPos();
                    }
                }
            }
        }
        if (structureTable == null) {
            fail("no table was found on the structure");
            return;
        }
        System.out.println("[packscene] the table on the structure is at " + structureTable + ", in the world at "
                + WorldSpace.get().centerInWorld(level, structureTable) + ", turned " + WorldSpace.get().yawOf(level, structureTable));
        player.teleportTo(level, placed.getX() - 3.5, placed.getY() + 1, placed.getZ() + 5.5, 215f, 25f);
    }

    /** Sits the player at the table on the structure and starts a game there. */
    private static void sitAtTheStructure(MinecraftServer server, ServerPlayer player) {
        if (structureTable == null) {
            return;
        }
        ServerLevel level = server.overworld();
        var seats = TableClusters.at(level, structureTable).seats();
        if (seats.isEmpty()) {
            fail("the table on the structure has no seats");
            return;
        }
        TableSeats.take(level, structureTable, seats.get(0).cell(), seats.get(0).side(), player.getUUID());
        TableSessions.Outcome outcome = TableSessions.start(level, structureTable, MatchRules.single(FormatPresets.MODERN));
        System.out.println("[packscene] a game at the table on the structure: " + outcome);
        dev.gathering.server.TableActions.openFor(player, structureTable);
    }

    private static void place(ServerLevel level, BlockPos origin) {
        var table = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            level.setBlock(part.offsetFrom(origin), table.setValue(TableBlock.PART, part), 3);
        }
    }

    private interface ServerStep {
        void run(MinecraftServer server, ServerPlayer player);
    }

    private static void onTheServer(Minecraft client, ServerStep what) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            fail("no single-player server to build on");
            return;
        }
        UUID who = client.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(who);
            if (player == null) {
                fail("the player was not on the server");
                return;
            }
            try {
                what.run(server, player);
            } catch (RuntimeException wentWrong) {
                wentWrong.printStackTrace();
                fail("a server step threw " + wentWrong);
            }
        });
    }

    private static void makeAWorld(Minecraft client) {
        LevelSettings settings = new LevelSettings(LEVEL, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel(LEVEL, settings, new WorldOptions(1L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(), null);
    }

    private static void shoot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), message -> { });
        System.out.println("[packscene] photographed " + name);
    }

    private static void advance(int settle) {
        step++;
        waited = settle;
        ticks = 0;
    }

    private static void fail(String what) {
        FAILURES.add(what);
        System.out.println("[packscene] FAIL " + what);
    }

    private static void finish(Minecraft client) {
        if (event != null) {
            Events.removeForTesting(event);
        }
        System.out.println("[packscene] reached step " + step);
        System.out.println("[packscene] failures: " + FAILURES.size());
        new File(client.gameDirectory, "screenshots").mkdirs();
        step = Integer.MAX_VALUE;
        client.stop();
    }
}
