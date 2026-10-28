package dev.gathering.core.loaner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class LoanerShelfTest {

    @Test
    void aFileNameBecomesSomethingToPutOnAButton() {
        assertThat(LoanerShelf.nameOf("mono-red_burn.txt")).isEqualTo("Mono Red Burn");
        assertThat(LoanerShelf.nameOf("Elves.txt")).isEqualTo("Elves");
        assertThat(LoanerShelf.nameOf("  spaced   out .dec ")).isEqualTo("Spaced Out");
    }

    @Test
    void aNameLongerThanAButtonIsCutToOne() {
        String name = LoanerShelf.nameOf("a".repeat(200) + ".txt");
        assertThat(name.length()).isLessThanOrEqualTo(LoanerShelf.MOST_NAME_CHARACTERS);
    }

    @Test
    void aBlankListIsNotOffered() {
        LoanerShelf shelf = LoanerShelf.of(Map.of("empty.txt", "   \n\n  "));
        assertThat(shelf.isEmpty()).isTrue();
    }

    @Test
    void twoFilesThatTidyToTheSameNameAreBothStillReachable() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("mono-red.txt", "4 Lightning Bolt");
        files.put("mono_red.txt", "4 Shock");
        LoanerShelf shelf = LoanerShelf.of(files);

        assertThat(shelf.size()).isEqualTo(2);
        assertThat(shelf.names()).doesNotHaveDuplicates();
        // Both lists survived: the second did not overwrite the first.
        assertThat(shelf.decks().stream().map(LoanerShelf.Loaner::decklist))
                .containsExactlyInAnyOrder("4 Lightning Bolt", "4 Shock");
    }

    @Test
    void theShelfIsTheSameOrderWhateverOrderTheFilesArrivedIn() {
        Map<String, String> one = new LinkedHashMap<>();
        one.put("zebra.txt", "1 Forest");
        one.put("aardvark.txt", "1 Island");
        Map<String, String> other = new LinkedHashMap<>();
        other.put("aardvark.txt", "1 Island");
        other.put("zebra.txt", "1 Forest");

        assertThat(LoanerShelf.of(one).names()).isEqualTo(LoanerShelf.of(other).names());
        assertThat(LoanerShelf.of(one).names()).containsExactly("Aardvark", "Zebra");
    }

    @Test
    void aShelfNeverOffersMoreThanAMenuCanHold() {
        Map<String, String> files = new LinkedHashMap<>();
        for (int index = 0; index < LoanerShelf.MOST * 3; index++) {
            files.put("deck" + index + ".txt", "1 Forest");
        }
        assertThat(LoanerShelf.of(files).size()).isEqualTo(LoanerShelf.MOST);
    }

    @Property
    void everyNameOfferedCanBeAskedForAgain(@ForAll("shelves") Map<String, String> files) {
        LoanerShelf shelf = LoanerShelf.of(files);
        for (String name : shelf.names()) {
            assertThat(shelf.byName(name)).isPresent();
        }
        assertThat(shelf.names()).doesNotHaveDuplicates();
        assertThat(shelf.names()).allSatisfy(name -> {
            assertThat(name).isNotBlank();
            assertThat(name.length()).isLessThanOrEqualTo(LoanerShelf.MOST_NAME_CHARACTERS);
        });
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<Map<String, String>> shelves() {
        return net.jqwik.api.Arbitraries.strings()
                .withCharRange('a', 'e').ofMinLength(0).ofMaxLength(4)
                .list().ofMaxSize(12)
                .map(names -> {
                    Map<String, String> files = new LinkedHashMap<>();
                    for (int index = 0; index < names.size(); index++) {
                        files.put(names.get(index) + index % 3 + ".txt", "1 Forest");
                    }
                    return files;
                });
    }

    @Test
    void aFileNameThatTidiesToNothingIsNotOffered() {
        assertThat(LoanerShelf.of(Map.of(".txt", "1 Forest")).isEmpty()).isTrue();
        assertThat(LoanerShelf.of(Map.of("---.txt", "1 Forest")).isEmpty()).isTrue();
    }

    @Test
    void nothingAtAllIsAnEmptyShelfRatherThanAFailure() {
        assertThat(LoanerShelf.of(null)).isEqualTo(LoanerShelf.EMPTY);
        assertThat(LoanerShelf.of(Map.of())).isEqualTo(LoanerShelf.EMPTY);
        assertThat(LoanerShelf.EMPTY.byName("anything")).isEmpty();
    }
}
