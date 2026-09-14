package dev.gathering.core.tournament;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One round's pairings.
 *
 * @param number      from one
 * @param elimination whether this is a round of the top cut, where a match cannot be drawn
 * @param pairings    every table, byes last
 * @param timeCalled  whether the round clock has run out
 */
public record Round(int number, boolean elimination, List<Pairing> pairings, boolean timeCalled) {

    public Round {
        pairings = List.copyOf(pairings);
    }

    public boolean isComplete() {
        return pairings.stream().allMatch(Pairing::isConfirmed);
    }

    public Optional<Pairing> pairingOf(UUID player) {
        return pairings.stream().filter(pairing -> pairing.has(player)).findFirst();
    }

    public Optional<Pairing> atTable(int table) {
        return pairings.stream().filter(pairing -> pairing.table() == table && !pairing.isBye()).findFirst();
    }

    Round with(Pairing replaced) {
        List<Pairing> changed = new java.util.ArrayList<>(pairings);
        for (int index = 0; index < changed.size(); index++) {
            Pairing old = changed.get(index);
            if (old.a().equals(replaced.a()) && old.table() == replaced.table()) {
                changed.set(index, replaced);
            }
        }
        return new Round(number, elimination, changed, timeCalled);
    }

    Round withTimeCalled() {
        List<Pairing> started = pairings.stream()
                .map(pairing -> pairing.isConfirmed() ? pairing : pairing.withTurnsAfterTime(0)).toList();
        return new Round(number, elimination, started, true);
    }
}
