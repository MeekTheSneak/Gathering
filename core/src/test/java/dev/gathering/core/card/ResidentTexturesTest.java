package dev.gathering.core.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Which card textures are let go of when the cache is over its caps")
class ResidentTexturesTest {

    private static final long NORMAL = TextureBudget.Tier.NORMAL.bytes();
    private static final long CRISP = TextureBudget.Tier.CRISP.bytes();

    private static final long ROOMY = Long.MAX_VALUE / 4;

    /** A board's worth of ordinary card art, oldest first. */
    private static List<ResidentTextures.Held> board(int howMany) {
        List<ResidentTextures.Held> held = new ArrayList<>(howMany);
        for (int index = 0; index < howMany; index++) {
            held.add(new ResidentTextures.Held("board-" + index, NORMAL, false));
        }
        return held;
    }

    @Test
    @DisplayName("a third card read does not take the whole board's art with it")
    void thecrispCapIsPaidOutOfCrispTextures() {
        // The bug this exists for. Three crisp textures are two too many by one, and they are
        // by definition the three most recently looked at - so oldest-first walked every
        // board texture on the table before reaching one that brought the count down, and a
        // player reading their third card in a row watched the felt re-download itself.
        List<ResidentTextures.Held> resident = board(60);
        resident.add(new ResidentTextures.Held("read-1", CRISP, true));
        resident.add(new ResidentTextures.Held("read-2", CRISP, true));
        resident.add(new ResidentTextures.Held("read-3", CRISP, true));

        List<String> going = ResidentTextures.toRelease(resident, 256, ROOMY, 2);

        assertThat(going).containsExactly("read-1");
    }

    @Test
    @DisplayName("nothing goes while everything fits")
    void afittingCacheIsLeftAlone() {
        assertThat(ResidentTextures.toRelease(board(10), 256, ROOMY, 2)).isEmpty();
        assertThat(ResidentTextures.toRelease(List.of(), 256, ROOMY, 2)).isEmpty();
        assertThat(ResidentTextures.toRelease(null, 256, ROOMY, 2)).isEmpty();
    }

    @Test
    @DisplayName("the count ceiling is paid oldest first, whatever tier that is")
    void thecountCeilingIsPlainlyOldestFirst() {
        List<String> going = ResidentTextures.toRelease(board(10), 7, ROOMY, 2);

        assertThat(going).containsExactly("board-0", "board-1", "board-2");
    }

    @Test
    @DisplayName("the byte ceiling is paid oldest first too, and stops the moment it fits")
    void thebyteCeilingStopsWhenItFits() {
        List<String> going = ResidentTextures.toRelease(board(10), 256, NORMAL * 8, 2);

        assertThat(going).containsExactly("board-0", "board-1");
    }

    @Test
    @DisplayName("a texture released for the byte ceiling is not counted against the tier again")
    void thetwoCapsDoNotDoubleCount() {
        // Both caps are over at once and the crisp textures are the oldest things there. If
        // the tier pass did not know what the first pass had already taken, it would name the
        // same key twice and the cache would release a texture it no longer holds.
        List<ResidentTextures.Held> resident = new ArrayList<>();
        resident.add(new ResidentTextures.Held("read-1", CRISP, true));
        resident.add(new ResidentTextures.Held("read-2", CRISP, true));
        resident.add(new ResidentTextures.Held("read-3", CRISP, true));
        resident.addAll(board(4));

        List<String> going = ResidentTextures.toRelease(resident, 5, ROOMY, 2);

        assertThat(going).containsExactly("read-1", "read-2");
        assertThat(going).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("the card being read now is the last crisp texture to go, never the first")
    void thenewestCrispSurvives() {
        List<ResidentTextures.Held> resident = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            resident.add(new ResidentTextures.Held("read-" + index, CRISP, true));
        }

        List<String> going = ResidentTextures.toRelease(resident, 256, ROOMY, 2);

        assertThat(going).containsExactly("read-1", "read-2", "read-3");
        assertThat(going).doesNotContain("read-5");
    }
}
