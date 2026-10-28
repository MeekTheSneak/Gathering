package dev.gathering.neoforge.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.gathering.Gathering;
import dev.gathering.core.text.ManaSymbols;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The symbol font and the code that writes into it, checked against each other.
 * <p>Three artifacts have to agree on which glyph is which: the name list in
 * {@code ManaSymbols}, the generator that draws the textures, and the font definition that
 * maps codepoints to them. Nothing connects them at compile time, and getting it wrong does
 * not fail - it silently draws the wrong symbol, which on a card is a different cost.
 * <p>So the font file is read back here and matched against the list, which is the one thing
 * that catches a glyph inserted rather than appended.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManaFontGameTest {

    private static final String FONT = "/assets/gathering/font/mana.json";

    @GameTest(template = "empty")
    public static void everySymbolHasAGlyphAtTheRightCodepoint(GameTestHelper helper) {
        JsonArray providers = providers();

        if (providers.size() != ManaSymbols.NAMES.size()) {
            helper.fail("The font has " + providers.size() + " glyphs but the code knows "
                    + ManaSymbols.NAMES.size());
        }

        for (int index = 0; index < providers.size(); index++) {
            JsonObject provider = providers.get(index).getAsJsonObject();
            String name = ManaSymbols.NAMES.get(index);

            String file = provider.get("file").getAsString();
            String expectedFile = "gathering:font/mana/" + name + ".png";
            if (!file.equals(expectedFile)) {
                helper.fail("Glyph " + index + " should be " + expectedFile + " but is " + file);
            }

            String chars = provider.getAsJsonArray("chars").get(0).getAsString();
            int expected = ManaSymbols.FIRST_CODEPOINT + index;
            if (chars.length() != 1 || chars.charAt(0) != expected) {
                helper.fail("Glyph for " + name + " sits at the wrong codepoint: " + (int) chars.charAt(0)
                        + " rather than " + expected);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyGlyphTextureIsActuallyThere(GameTestHelper helper) {
        for (String name : ManaSymbols.NAMES) {
            String path = "/assets/gathering/textures/font/mana/" + name + ".png";
            try (InputStream stream = ManaFontGameTest.class.getResourceAsStream(path)) {
                if (stream == null) {
                    helper.fail("No texture for the " + name + " symbol at " + path);
                    return;
                }
            } catch (IOException e) {
                throw new GameTestAssertException("Could not read " + path + ": " + e);
            }
        }
        helper.succeed();
    }

    private static JsonArray providers() {
        try (InputStream stream = ManaFontGameTest.class.getResourceAsStream(FONT)) {
            if (stream == null) {
                throw new GameTestAssertException("The mana font definition is not on the classpath");
            }
            JsonObject root = JsonParser
                    .parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return root.getAsJsonArray("providers");
        } catch (IOException e) {
            throw new GameTestAssertException("Could not read the mana font definition: " + e);
        }
    }
}
