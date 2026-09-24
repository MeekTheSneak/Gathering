package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.client.ClientTableHighlight;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.SeatId;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the cursor is on at one table is not lit at the table next door.
 * <p>{@code ClientTableHighlight} is what the screen leaves for the world renderer: the card
 * under the cursor, the ones picked, the one in the air, the pile and the mat it is aimed at, and
 * the button the cursor rests on. It was kept by seat and card number alone, and the renderer
 * asks it once for every table in view. Seats are numbered from zero at every table and card
 * numbers are counted per game, so a neighbor's seat 0 and card 12 were yours as far as it could
 * tell: their button lit, their card 12 ringed, and their card 12 left off the felt altogether
 * while you carried yours.
 * <p>A game test rather than a unit test because the holder is keyed by a {@link BlockPos}, which
 * is Minecraft's and so out of reach of {@code :core:test}. It has nothing else from the client
 * in it, so the game-test server can load it.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TableHighlightGameTest {

    /** Two tables side by side, somewhere no real table is. */
    private static final BlockPos MINE = new BlockPos(0, Integer.MIN_VALUE, 11);
    private static final BlockPos NEXT_DOOR = new BlockPos(3, Integer.MIN_VALUE, 11);

    private static final SeatId SEAT = SeatId.of(0);
    private static final CardInstanceId UNDER = CardInstanceId.of(12);
    private static final CardInstanceId PICKED = CardInstanceId.of(7);
    private static final CardInstanceId HELD = CardInstanceId.of(3);
    private static final int PILE = 1;
    private static final int VERB = 2;

    /**
     * All of it in one test, on purpose: the holder is one set of statics, and tests in a grid
     * run side by side. Each check says what it checks, so a failure still names the thing.
     */
    @GameTest(template = "empty")
    public static void ahighlightstaysatitsowntable(GameTestHelper helper) {
        try {
            everythingLitHereIsLitHere(helper);
            nothingLitHereIsLitNextDoor(helper);
            anotherTableStartsFromNothing(helper);
            clearingForgetsEveryTable(helper);
            helper.succeed();
        } finally {
            ClientTableHighlight.clear();
        }
    }

    /** What the screen at this table says each frame, in the order it says it. */
    private static void lightMine() {
        ClientTableHighlight.clear();
        ClientTableHighlight.set(MINE, UNDER, List.of(PICKED), HELD);
        ClientTableHighlight.pointAtVerb(MINE, SEAT, VERB);
        ClientTableHighlight.aimAt(MINE, SEAT, PILE);
        ClientTableHighlight.landingOn(MINE, SEAT);
    }

    /**
     * The fixture lights all of it at its own table. Without this the checks below pass for a
     * holder that lights nothing anywhere.
     */
    private static void everythingLitHereIsLitHere(GameTestHelper helper) {
        lightMine();
        // A position equal to the one written, not the same object: the renderer is handed the
        // block entity's own, and the screen was opened with another.
        BlockPos here = new BlockPos(MINE.getX(), MINE.getY(), MINE.getZ());
        List<String> missing = unlit(here);
        if (!missing.isEmpty()) {
            helper.fail("at the table the cursor is on, these were not lit: " + missing);
        }
    }

    private static void nothingLitHereIsLitNextDoor(GameTestHelper helper) {
        lightMine();
        List<String> lit = lit(NEXT_DOOR);
        if (!lit.isEmpty()) {
            helper.fail("the table next door lit what the cursor is on at mine: " + lit);
        }
    }

    /**
     * A screen at another table does not pick up where the last one left off.
     * <p>Each writer says one part of what the cursor is doing, so a card hovered at one table
     * and a drag aimed at the next would otherwise be read together as the second table's.
     */
    private static void anotherTableStartsFromNothing(GameTestHelper helper) {
        lightMine();
        ClientTableHighlight.aimAt(NEXT_DOOR, null, -1);
        List<String> lit = lit(NEXT_DOOR);
        if (!lit.isEmpty()) {
            helper.fail("a table the screen had just moved to still had the last table's: " + lit);
            return;
        }
        List<String> left = lit(MINE);
        if (!left.isEmpty()) {
            helper.fail("the table the screen left still had what the cursor was on there: " + left);
        }
    }

    private static void clearingForgetsEveryTable(GameTestHelper helper) {
        lightMine();
        ClientTableHighlight.clear();
        List<String> lit = lit(MINE);
        if (!lit.isEmpty()) {
            helper.fail("clearing left this lit: " + lit);
        }
    }

    /** Everything the world renderer asks a table about, and which of it the answer was yes to. */
    private static List<String> lit(BlockPos table) {
        List<String> lit = new java.util.ArrayList<>();
        if (ClientTableHighlight.isLit(table, UNDER)) {
            lit.add("the card under the cursor");
        }
        if (ClientTableHighlight.isLit(table, PICKED)) {
            lit.add("the picked card");
        }
        if (ClientTableHighlight.isInTheAir(table, HELD)) {
            lit.add("the card in the air, which is left off the felt");
        }
        if (ClientTableHighlight.isAimedAt(table, SEAT, PILE)) {
            lit.add("the pile aimed at");
        }
        if (ClientTableHighlight.isLandingOn(table, SEAT)) {
            lit.add("the mat landed on");
        }
        if (ClientTableHighlight.isPointedAtVerb(table, SEAT, VERB)) {
            lit.add("the button pointed at");
        }
        return lit;
    }

    private static List<String> unlit(BlockPos table) {
        List<String> all = List.of("the card under the cursor", "the picked card",
                "the card in the air, which is left off the felt", "the pile aimed at",
                "the mat landed on", "the button pointed at");
        List<String> lit = lit(table);
        return all.stream().filter(said -> !lit.contains(said)).toList();
    }
}
