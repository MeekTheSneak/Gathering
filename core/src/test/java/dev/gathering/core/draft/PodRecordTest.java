package dev.gathering.core.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.card.CardIdentity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What an event's packs held, saved and read back, and turned into rounds and pools. */
class PodRecordTest {

    private static final UUID HOST = new UUID(8L, 0L);
    private static final UUID GUEST = new UUID(8L, 1L);

    private static List<CardIdentity> cards(int from, int count) {
        List<CardIdentity> cards = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            cards.add(CardIdentity.ofPrinting(new UUID(9L, from + index), index % 3 == 0));
        }
        return cards;
    }

    private static PodRecord record() {
        return new PodRecord(HOST, PodSettings.CardsGo.TO_CONTRIBUTORS, List.of(HOST, GUEST),
                List.of(Arrays.asList(HOST, null), List.of(GUEST, GUEST)),
                List.of(List.of(cards(0, 3), cards(10, 3)), List.of(cards(20, 3), cards(30, 3))),
                "1, 2, 3");
    }

    @Test
    void aRecordRoundTripsThroughItsBytes() throws Exception {
        PodRecord record = record();
        assertThat(PodRecord.read(PodRecord.write(record))).isEqualTo(record);
    }

    @Test
    void theClockIsSavedWithTheRecord() throws Exception {
        PodRecord old = record();
        PodRecord clocked = new PodRecord(old.host(), old.cardsGo(), old.seated(), old.contributors(), old.opened(),
                old.podName(), 45);
        assertThat(PodRecord.read(PodRecord.write(clocked)).pickSeconds()).isEqualTo(45);
    }

    /** A pod saved before the clock existed still opens, with no clock. */
    @Test
    void aRecordSavedBeforeTheClockStillReads() throws Exception {
        PodRecord record = record();
        byte[] now = PodRecord.write(record);
        // Version 1 is version 2 without the four bytes of the clock after the pod's name.
        int nameEnds = 4 + 16 + 2 + record.cardsGo().name().length() + 2 + record.podName().length();
        byte[] old = new byte[now.length - 4];
        System.arraycopy(now, 0, old, 0, nameEnds);
        System.arraycopy(now, nameEnds + 4, old, nameEnds, now.length - nameEnds - 4);
        old[3] = 1;
        assertThat(PodRecord.read(old)).isEqualTo(record);
    }

    @Test
    void roundsAreEachSeatsNthPack() {
        List<List<DraftPack>> rounds = record().rounds();
        assertThat(rounds).hasSize(2);
        assertThat(rounds.get(1).get(0).cards()).isEqualTo(cards(10, 3));
        assertThat(rounds.get(0).get(1).cards()).isEqualTo(cards(20, 3));
    }

    /** A made pack in an event that could not finish goes to nobody; everything else goes home. */
    @Test
    void anUnfinishedEventSendsEachPackHomeAndMadePacksNowhere() {
        var back = record().backToContributors();
        assertThat(back.get(HOST)).isEqualTo(cards(0, 3));
        List<CardIdentity> guest = new ArrayList<>(cards(20, 3));
        guest.addAll(cards(30, 3));
        assertThat(back.get(GUEST)).isEqualTo(guest);
    }

    @Test
    void aRecordThatDoesNotAddUpIsRefused() {
        assertThatThrownBy(() -> new PodRecord(HOST, PodSettings.CardsGo.PLAYERS_KEEP, List.of(HOST),
                List.of(List.of(HOST)), List.of(List.of(cards(0, 1), cards(1, 1))), ""))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] bytes = PodRecord.write(record());
        bytes[3] = 9;
        assertThatThrownBy(() -> PodRecord.read(bytes)).isInstanceOf(java.io.IOException.class);
    }
}
