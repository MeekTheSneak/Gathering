package dev.gathering.client;

import dev.gathering.core.match.TableTerms;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the board says about a table when the row is too narrow to say all of it.
 * <p>The row offers the sentence at several lengths and the board takes the first that fits, so the
 * order of that ladder decides what a player loses first. It used to lose the words: "Turn 3 - Dev ·
 * free play" and then, one rung down, "Turn 3 - Dev · !" - a bare mark, with the meaning only in a
 * tooltip nobody knows to hover. A name a few letters longer than the scripted run's was enough to
 * land there, which is to say most real names.
 * <p>The name should go first. It is already up in the seat columns, with a face and a color beside
 * it; "for keeps" and "free play" are said nowhere else on the board.
 */
@GameTestHolder("gathering")
@PrefixGameTestTemplate(false)
public final class TableTermsTextGameTest {

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @GameTest(template = "empty")
    public static void anarrowRowDropsTheNameBeforeTheWords(GameTestHelper helper) {
        // A table nobody named a format for: free play, which the board says in its warning color.
        TableTerms terms = new TableTerms("", 1, 1, true, false, false, 0);
        Component turn = Component.literal("Turn 1 - Somebodyorother");
        Component turnAlone = Component.literal("Turn 1");

        List<Component> ways = TableTermsText.candidates(turn, turnAlone, terms);
        require(!ways.isEmpty(), "the board offered no way of saying anything");

        // The longest way is everything: the turn, then the terms after it. Everything after the
        // turn is what the words cost, whatever language this is running in.
        String longest = ways.getFirst().getString();
        require(longest.startsWith(turn.getString()),
                "the longest way did not begin with the turn: " + longest);
        String words = longest.substring(turn.getString().length());
        String shortened = turnAlone.getString() + words;
        require(shortened.length() < longest.length(), "dropping the name saved nothing");

        int saysItShort = -1;
        int bareMark = -1;
        for (int at = 0; at < ways.size(); at++) {
            String way = ways.get(at).getString();
            if (saysItShort < 0 && way.equals(shortened)) {
                saysItShort = at;
            }
            if (bareMark < 0 && way.endsWith("!")) {
                bareMark = at;
            }
        }
        require(saysItShort >= 0, "no way of saying the terms without the name: " + ways.stream()
                .map(Component::getString).toList());
        require(bareMark >= 0, "the board never falls back to a bare mark at all");
        require(saysItShort < bareMark,
                "the board falls to a bare mark before it tries saying the terms without the name");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void therowOfItsOwnStillSaysEverything(GameTestHelper helper) {
        // A window wide enough to give the terms their own row passes no turn at all, and the
        // ladder still has to hold: longest first, and an empty line last rather than missing.
        List<Component> ways =
                TableTermsText.candidates(null, null, new TableTerms("", 1, 1, true, false, false, 0));
        require(ways.size() >= 2, "a row of its own was offered " + ways.size() + " way(s) to say it");
        require(ways.getLast().getString().isEmpty(), "the last resort was not an empty line");
        require(!ways.getFirst().getString().isEmpty(), "the longest way said nothing");
        helper.succeed();
    }
}
