package dev.gathering.core.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManaSymbolsTest {

    @Test
    @DisplayName("a tap ability splits into symbols and words")
    void oracleTextSplitsIntoRuns() {
        List<ManaSymbols.Segment> segments = ManaSymbols.segments("{T}: Add {C}{C}.");

        assertThat(segments).hasSize(4);
        assertThat(segments.get(0).symbols()).isTrue();
        assertThat(segments.get(1).text()).isEqualTo(": Add ");
        assertThat(segments.get(2).symbols()).isTrue();
        // Adjacent symbols share one run, so this is two glyphs and not two segments.
        assertThat(segments.get(2).text()).hasSize(2);
        assertThat(segments.get(3).text()).isEqualTo(".");
    }

    @Test
    @DisplayName("a symbol nobody has drawn is left exactly as written")
    void unknownSymbolsSurviveAsText() {
        List<ManaSymbols.Segment> segments = ManaSymbols.segments("Pay {W/Q} or {}");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).symbols()).isFalse();
        assertThat(segments.get(0).text()).isEqualTo("Pay {W/Q} or {}");
    }

    @Test
    @DisplayName("hybrids read the same whichever way round they are written")
    void hybridOrderDoesNotMatter() {
        assertThat(ManaSymbols.nameFor("W/U")).isEqualTo("wu");
        assertThat(ManaSymbols.nameFor("U/W")).isEqualTo("wu");
        assertThat(ManaSymbols.nameFor("2/W")).isEqualTo("2w");
    }

    @Test
    @DisplayName("every name has its own glyph")
    void namesAreUnique() {
        assertThat(ManaSymbols.NAMES).doesNotHaveDuplicates();
    }

    @Property(tries = 2000)
    void everyLetterOfTheInputSurvivesOrBecomesASymbol(@ForAll("cardish") String text) {
        List<ManaSymbols.Segment> segments = ManaSymbols.segments(text);

        // Nothing is silently dropped: rebuilding the text from its letter runs and the
        // codes the symbol runs stand for gives back exactly what went in.
        StringBuilder rebuilt = new StringBuilder();
        for (ManaSymbols.Segment segment : segments) {
            if (segment.symbols()) {
                for (int index = 0; index < segment.text().length(); index++) {
                    int glyph = segment.text().charAt(index) - ManaSymbols.FIRST_CODEPOINT;
                    assertThat(glyph).isBetween(0, ManaSymbols.NAMES.size() - 1);
                    rebuilt.append('{').append(ManaSymbols.NAMES.get(glyph)).append('}');
                }
            } else {
                rebuilt.append(segment.text());
            }
        }
        assertThat(canonical(rebuilt.toString())).isEqualTo(canonical(text));
    }

    @Property(tries = 2000)
    void neverThrowsOnAnythingAtAll(@ForAll("cardish") String text) {
        assertThat(ManaSymbols.segments(text)).isNotNull();
    }

    /** Rewrites recognized codes into their canonical name so the two sides are comparable. */
    private static String canonical(String text) {
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int open = text.indexOf('{', index);
            int close = open < 0 ? -1 : text.indexOf('}', open);
            if (open < 0 || close < 0) {
                out.append(text, index, text.length());
                break;
            }
            String name = ManaSymbols.nameFor(text.substring(open + 1, close));
            out.append(text, index, open);
            out.append('{').append(name == null ? text.substring(open + 1, close) : name).append('}');
            index = close + 1;
        }
        return out.toString();
    }

    @Provide
    Arbitrary<String> cardish() {
        Arbitrary<String> pieces = Arbitraries.of(
                "{T}", "{W}", "{2}", "{W/U}", "{U/W}", "{2/R}", "{W/P}", "{Q}", "{E}", "{}", "{ZZZ}",
                "{", "}", ": Add ", "Flying", ", ", ".", "\n", " ", "{20}", "{X}", "Sacrifice");
        return pieces.list().ofMaxSize(12).map(list -> String.join("", list));
    }
}
