package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.TextureBudget;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The video-memory budget is worked out from the cap the client actually ships.
 * <p>{@link TextureBudget} is in the pure module and cannot see
 * {@code ClientCardImages.MAX_RESIDENT_TEXTURES}, so its test carries a copy of that number.
 * This is the one line that stops the copy going stale: raise the cap and the arithmetic that
 * says what it costs still describes the old one, which is how the brief came to claim a budget
 * nothing had multiplied out.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TextureBudgetGameTest {

    /** What {@code TextureBudgetTest} assumes the client's cap is. */
    private static final int ASSUMED_CAP = 256;

    @GameTest(template = "empty")
    public static void theBudgetKnowsTheRealCap(GameTestHelper helper) {
        int real = dev.gathering.client.ClientCardImages.MAX_RESIDENT_TEXTURES;
        if (real != ASSUMED_CAP) {
            helper.fail("The client keeps " + real + " textures resident and the budget is "
                    + "worked out for " + ASSUMED_CAP + " - see TextureBudgetTest, and the "
                    + "figure in section 8 of the design brief");
            return;
        }
        long worst = TextureBudget.mebibytesFor(TextureBudget.Tier.NORMAL, real);
        if (worst > TextureBudget.CEILING_MEBIBYTES) {
            helper.fail("The resident cap now costs " + worst + " MiB of video memory, past "
                    + "the " + TextureBudget.CEILING_MEBIBYTES + " written down");
            return;
        }
        helper.succeed();
    }
}
