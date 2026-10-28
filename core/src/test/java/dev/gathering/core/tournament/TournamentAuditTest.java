package dev.gathering.core.tournament;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TournamentAuditTest {
    @Test void rejectsOverflowingScore() {
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(Integer.MAX_VALUE, Integer.MAX_VALUE - 1, 0));
    }

    @Test void byeSelectionRetainsResultsAgainstDroppedOpponents() {
        Entrant a = Entrant.registering(new UUID(0,1), "A", 1400);
        Entrant b = Entrant.registering(new UUID(0,2), "B", 1600);
        Entrant c = Entrant.registering(new UUID(0,3), "C", 1500);
        Entrant d = Entrant.registering(new UUID(0,4), "D", 1700);
        Round previous = new Round(1, false, List.of(
                Pairing.of(1,a.id(),b.id()).settled(new MatchResult(0,2,0)),
                Pairing.of(2,c.id(),d.id()).settled(new MatchResult(2,0,0))), false);
        assertEquals(3, Standings.of(List.of(a,b,c,d),List.of(previous)).stream()
                .filter(r -> r.player().id().equals(b.id())).findFirst().orElseThrow().matchPoints());
        List<Pairing> next = SwissPairer.pair(List.of(b,c,d),List.of(previous),2);
        assertEquals(d.id(),next.stream().filter(Pairing::isBye).findFirst().orElseThrow().a(),
                "D is the only winless entrant, but dropping A erased B's win during pairing");
    }
}
