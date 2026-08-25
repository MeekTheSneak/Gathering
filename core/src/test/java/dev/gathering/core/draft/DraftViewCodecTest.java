package dev.gathering.core.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.card.CardIdentity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/**
 * A pod view, written and read back.
 *
 * <p>A codec that encodes fine and decodes wrong is invisible until two people try to draft,
 * so every field gets a round trip rather than a reading.
 */
class DraftViewCodecTest {

    /** Everything a drafter is shown survives the wire. */
    @Test
    void aViewComesBackTheSame() throws IOException {
        DraftState pod = someWayIn(pod(6, 9), 2);
        DraftView sent = DraftVisibility.viewFor(pod, DrafterId.of(2));

        DraftView arrived = DraftViewCodec.read(DraftViewCodec.write(sent));

        assertThat(arrived).isEqualTo(sent);
    }

    /** Including a foil, and a custom card, which take the other branch of an identity. */
    @Test
    void aFoilAndACustomCardSurviveToo() throws IOException {
        DraftView sent = new DraftView(
                DrafterId.of(0), 4, 0, 3, false,
                DraftPack.of(List.of(
                        CardIdentity.ofPrinting(UUID.randomUUID(), true),
                        CardIdentity.ofCustom("server:goblin", false))),
                List.of(CardIdentity.ofCustom("server:another", true)),
                false, 2, List.of(DrafterId.of(1)),
                java.util.Map.of(DrafterId.of(0), 0),
                java.util.Map.of(DrafterId.of(0), 2));

        assertThat(DraftViewCodec.read(DraftViewCodec.write(sent))).isEqualTo(sent);
    }

    /** And a finished pod, which holds no pack at all. */
    @Test
    void aFinishedPodComesBackFinished() throws IOException {
        DraftState pod = draftToTheEnd(pod(4, 4));
        DraftView sent = DraftVisibility.viewFor(pod, DrafterId.of(0));

        DraftView arrived = DraftViewCodec.read(DraftViewCodec.write(sent));

        assertThat(arrived.finished()).isTrue();
        assertThat(arrived.myPack().isEmpty()).isTrue();
        assertThat(arrived).isEqualTo(sent);
    }

    /** A version this does not read is refused rather than misread as something else. */
    @Test
    void anUnknownVersionIsRefused() {
        byte[] written = DraftViewCodec.write(
                DraftVisibility.viewFor(pod(4, 4), DrafterId.of(0)));
        written[3] = (byte) (written[3] + 1);

        assertThatThrownBy(() -> DraftViewCodec.read(written))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("version");
    }

    /**
     * A length nobody could mean is refused before it allocates anything.
     *
     * <p>Not a hypothetical: a length field is the cheapest way to turn a malformed packet
     * into an out-of-memory kill, so it is checked before it is used to size a list.
     */
    @Test
    void anImplausibleLengthIsRefusedBeforeItAllocates() {
        byte[] written = DraftViewCodec.write(
                DraftVisibility.viewFor(pod(4, 4), DrafterId.of(0)));
        // The pack's card count: past the version, place, four ints and two booleans.
        int at = 4 + 4 + 4 + 4 + 4 + 1 + 1 + 4;
        written[at] = 0x7F;
        written[at + 1] = (byte) 0xFF;
        written[at + 2] = (byte) 0xFF;
        written[at + 3] = (byte) 0xFF;

        assertThatThrownBy(() -> DraftViewCodec.read(written))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Implausible");
    }

    /** And over arbitrary pods rather than the ones somebody thought to write down. */
    @Property(tries = 200)
    void everyViewOfEveryPodComesBackTheSame(@ForAll("views") DraftView sent) throws IOException {
        assertThat(DraftViewCodec.read(DraftViewCodec.write(sent))).isEqualTo(sent);
    }

    @Provide
    Arbitrary<DraftView> views() {
        return Arbitraries.integers().between(4, 8).flatMap(drafters ->
                Arbitraries.integers().between(1, 9).flatMap(packSize ->
                        Arbitraries.integers().between(0, 5).flatMap(turns ->
                                Arbitraries.integers().between(0, drafters - 1).map(me ->
                                        DraftVisibility.viewFor(
                                                someWayIn(pod(drafters, packSize), turns),
                                                DrafterId.of(me))))));
    }

    // --- helpers ---

    private static DraftState pod(int drafters, int packSize) {
        List<List<DraftPack>> rounds = new ArrayList<>(DraftRules.ROUNDS);
        for (int round = 0; round < DraftRules.ROUNDS; round++) {
            List<DraftPack> packs = new ArrayList<>(drafters);
            for (int drafter = 0; drafter < drafters; drafter++) {
                List<CardIdentity> cards = new ArrayList<>(packSize);
                for (int card = 0; card < packSize; card++) {
                    cards.add(CardIdentity.ofPrinting(UUID.nameUUIDFromBytes(
                            ("r" + round + "d" + drafter + "c" + card)
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                }
                packs.add(DraftPack.of(cards));
            }
            rounds.add(packs);
        }
        return DraftState.opening(drafters, rounds);
    }

    private static DraftState someWayIn(DraftState pod, int turns) {
        DraftState after = pod;
        for (int turn = 0; turn < turns && !after.isFinished(); turn++) {
            after = oneTurn(after);
        }
        return after;
    }

    private static DraftState draftToTheEnd(DraftState pod) {
        DraftState after = pod;
        while (!after.isFinished()) {
            after = oneTurn(after);
        }
        return after;
    }

    private static DraftState oneTurn(DraftState pod) {
        DraftState after = pod;
        for (int index = 0; index < pod.drafters(); index++) {
            DrafterId drafter = DrafterId.of(index);
            int due = after.picksDueFrom(drafter);
            if (due == 0) {
                continue;
            }
            List<Integer> front = new ArrayList<>(due);
            for (int position = 0; position < due; position++) {
                front.add(position);
            }
            after = after.declare(drafter, front);
        }
        return after;
    }
}
