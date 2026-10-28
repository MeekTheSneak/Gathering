package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.TextureBudget;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The client keeps the number of textures the video-memory budget works out, and not a number of its own.
 * <p>{@link TextureBudget} is in the pure module and cannot see {@code ClientCardImages.MAX_RESIDENT_TEXTURES}, so
 * this is the one line that ties the two together. The cap used to be a literal beside the budget's arithmetic, and
 * the arithmetic described a different number: 256 for every tier, worked out from the normal one, so 256 small
 * faces - a ninth of the budget - filled the cache, and a hall of tables re-fetched its art every frame.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TextureBudgetGameTest {

    @GameTest(template = "empty")
    public static void theBudgetKnowsTheRealCap(GameTestHelper helper) {
        int real = dev.gathering.client.ClientCardImages.MAX_RESIDENT_TEXTURES;
        if (real != TextureBudget.MOST_TEXTURES) {
            helper.fail("The client keeps " + real + " textures resident and the budget works out "
                    + TextureBudget.MOST_TEXTURES + " - the ceiling filled with small faces - see TextureBudget, and "
                    + "the figure in section 8 of the design brief");
            return;
        }
        helper.succeed();
    }
}
