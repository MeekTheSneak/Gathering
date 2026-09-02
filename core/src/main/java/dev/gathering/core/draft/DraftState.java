package dev.gathering.core.draft;

import dev.gathering.core.card.CardIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A draft pod, entire, as a value.
 * <p>Everything the pod is: which round it is on, what each drafter is holding, what each
 * has picked, and what has been declared this turn but not yet resolved. Every transition
 * returns a new one, so a pod can be logged, replayed and reasoned about the same way a game
 * session can, and nothing about it depends on a table, a world or a player.
 * <p>A turn resolves all at once. Real drafting is simultaneous - everybody looks at their
 * pack, everybody picks, then the packs move together - and modeling it as a queue of
 * individual picks would have let one fast drafter see a pack twice while a slow one had not
 * seen it at all. So a pick is <em>declared</em>, and nothing moves until every drafter with
 * cards in front of them has declared. What anybody declared stays theirs alone until then,
 * which is also the whole of the privacy rule: an undeclared pack cannot leak a pick that
 * has not been made, and a declared one cannot leak it either, because declarations never
 * reach another drafter's view.
 */
public record DraftState(
        int drafters,
        int round,
        List<List<DraftPack>> opening,
        List<DraftPack> holding,
        Map<DrafterId, List<Integer>> declared,
        List<List<CardIdentity>> pools) {

    public DraftState {
        if (!DraftRules.isAPodSize(drafters)) {
            throw new IllegalArgumentException(
                    "A pod is " + DraftRules.SMALLEST_POD + " to " + DraftRules.LARGEST_POD
                            + " drafters, not " + drafters);
        }
        opening = deepCopy(opening);
        holding = holding == null ? List.of() : List.copyOf(holding);
        declared = declared == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(declared));
        List<List<CardIdentity>> sealed = new ArrayList<>();
        if (pools != null) {
            for (List<CardIdentity> pool : pools) {
                sealed.add(List.copyOf(pool));
            }
        }
        pools = List.copyOf(sealed);
    }

    /**
     * A pod about to open its first pack.
     *
     * @param opening per round, per drafter, the pack that drafter opens - which is the
     *                whole of the randomness in a draft, decided before anybody picks so the
     *                pod is reproducible from its opening packs and its picks alone
     */
    public static DraftState opening(int drafters, List<List<DraftPack>> opening) {
        if (opening == null || opening.isEmpty()) {
            throw new IllegalArgumentException("A pod needs at least one round of packs");
        }
        for (List<DraftPack> round : opening) {
            if (round == null || round.size() != drafters) {
                throw new IllegalArgumentException(
                        "Every round needs one pack per drafter: " + drafters + " expected");
            }
            for (DraftPack pack : round) {
                // A pack with nothing in it is a pod that can never finish. Nobody has a pick
                // due, so nobody can declare, so the turn never resolves and the round never
                // ends - the pod sits at the first round for ever with no way to move it on.
                // Refused here rather than handled later, because there is no sensible way to
                // draft from an empty pack and a stuck pod is far worse than a refusal.
                if (pack == null || pack.isEmpty()) {
                    throw new IllegalArgumentException("A pod cannot be dealt an empty pack");
                }
            }
        }
        List<List<CardIdentity>> pools = new ArrayList<>(drafters);
        for (int index = 0; index < drafters; index++) {
            pools.add(List.of());
        }
        return new DraftState(drafters, 0, opening, opening.get(0), Map.of(), pools);
    }

    /** Whether every pack has been emptied and every round played. */
    public boolean isFinished() {
        return round >= opening.size();
    }

    /** How many cards this drafter takes before the packs move on. */
    public int picksDueFrom(DrafterId drafter) {
        if (isFinished()) {
            return 0;
        }
        return Math.min(DraftRules.picksPerTurn(drafters), packHeldBy(drafter).size());
    }

    public DraftPack packHeldBy(DrafterId drafter) {
        require(drafter);
        return isFinished() ? new DraftPack(List.of()) : holding.get(drafter.index());
    }

    public List<CardIdentity> poolOf(DrafterId drafter) {
        require(drafter);
        return pools.get(drafter.index());
    }

    /** Whether this drafter has said what they are taking from the pack in front of them. */
    public boolean hasDeclared(DrafterId drafter) {
        require(drafter);
        return declared.containsKey(drafter);
    }

    /** Who the pod is still waiting on, in order, so a screen can say so by name. */
    public List<DrafterId> stillToPick() {
        List<DrafterId> waiting = new ArrayList<>();
        if (isFinished()) {
            return List.copyOf(waiting);
        }
        for (int index = 0; index < drafters; index++) {
            DrafterId drafter = DrafterId.of(index);
            if (picksDueFrom(drafter) > 0 && !declared.containsKey(drafter)) {
                waiting.add(drafter);
            }
        }
        return List.copyOf(waiting);
    }

    /**
     * Why this pick cannot be made, or empty when it can.
     * <p>Every reason, including the ones about the positions themselves. It used to be only
     * some of them: the pod asked whether the <em>number</em> of picks was right and let the
     * numbers through, and {@link #declare} threw on a position that was out of range or
     * repeated. That throw came out of a network payload handler - so a client naming card
     * 999 of a fifteen-card pack, or the same card twice, threw on the server thread. On one
     * loader that disconnects the client; on the other it reaches the server's task queue and
     * takes the server down. Neither is an answer to a stale pick, which is what this mostly
     * is: a drafter whose screen is a moment behind the pod.
     * <p>So the whole rule lives here and is asked before anything is done, and {@code
     * declare} throws only for a caller that did not ask - which no longer includes the wire.
     */
    public Optional<String> denialFor(DrafterId drafter, List<Integer> positions) {
        require(drafter);
        if (isFinished()) {
            return Optional.of("The pod has finished drafting");
        }
        if (declared.containsKey(drafter)) {
            return Optional.of(drafter + " has already picked this turn");
        }
        int due = picksDueFrom(drafter);
        if (due == 0) {
            return Optional.of(drafter + " has no pack to pick from");
        }
        List<Integer> chosen = positions == null ? List.of() : List.copyOf(positions);
        if (chosen.size() != due) {
            return Optional.of(drafter + " picks " + due + " this turn, not " + chosen.size());
        }
        DraftPack pack = packHeldBy(drafter);
        for (int index = 0; index < chosen.size(); index++) {
            Integer position = chosen.get(index);
            if (position == null || position < 0 || position >= pack.size()) {
                return Optional.of(
                        "There is no card at " + position + " in a pack of " + pack.size());
            }
            if (chosen.indexOf(position) != index) {
                return Optional.of("The same card cannot be picked twice: " + position);
            }
        }
        return Optional.empty();
    }

    /**
     * Declares what this drafter is taking, and resolves the turn if they were the last.
     *
     * @param positions places in the pack in front of them, as many as {@link #picksDueFrom}
     * @throws IllegalArgumentException if the pod is finished, this drafter has already
     *                                  declared, or the positions are the wrong number,
     *                                  repeated, or not in the pack
     */
    public DraftState declare(DrafterId drafter, List<Integer> positions) {
        String denial = denialFor(drafter, positions).orElse(null);
        if (denial != null) {
            throw new IllegalArgumentException(denial);
        }
        List<Integer> chosen = positions == null ? List.of() : List.copyOf(positions);

        Map<DrafterId, List<Integer>> now = new LinkedHashMap<>(declared);
        now.put(drafter, chosen);
        DraftState waiting = new DraftState(drafters, round, opening, holding, now, pools);
        return waiting.stillToPick().isEmpty() ? waiting.resolve() : waiting;
    }

    /**
     * Everybody takes what they said, and the packs move on.
     * <p>Private, because a turn resolving is not something anybody does - it is what has
     * happened once the last drafter has decided. Exposing it would be exposing a way to
     * make the packs move while somebody was still looking at one.
     */
    private DraftState resolve() {
        List<DraftPack> left = new ArrayList<>(drafters);
        List<List<CardIdentity>> grown = new ArrayList<>(drafters);
        for (int index = 0; index < drafters; index++) {
            DrafterId drafter = DrafterId.of(index);
            DraftPack pack = holding.get(index);
            List<Integer> taken = declared.getOrDefault(drafter, List.of());
            List<CardIdentity> pool = new ArrayList<>(pools.get(index));
            pool.addAll(pack.at(taken));
            grown.add(pool);
            left.add(pack.without(taken));
        }

        boolean anythingLeft = false;
        for (DraftPack pack : left) {
            if (!pack.isEmpty()) {
                anythingLeft = true;
                break;
            }
        }
        if (!anythingLeft) {
            // The round is over the moment the packs are empty, not a turn later. Passing
            // empty packs round the ring first would have every drafter declare nothing at
            // all several times before the next round opened.
            int next = round + 1;
            return next >= opening.size()
                    ? new DraftState(drafters, next, opening, List.of(), Map.of(), grown)
                    : new DraftState(drafters, next, opening, opening.get(next), Map.of(), grown);
        }

        // Round nought goes left, round one goes right, and so on: a pod that always passed
        // the same way would spend the whole draft reading one neighbor and being read by
        // the other.
        int way = DraftRules.passing(round);
        List<DraftPack> passed = new ArrayList<>(java.util.Collections.nCopies(drafters, null));
        for (int index = 0; index < drafters; index++) {
            passed.set(Math.floorMod(index + way, drafters), left.get(index));
        }
        return new DraftState(drafters, round, opening, passed, Map.of(), grown);
    }

    private void require(DrafterId drafter) {
        if (drafter == null || drafter.index() >= drafters) {
            throw new IllegalArgumentException("No such drafter in a pod of " + drafters + ": " + drafter);
        }
    }

    private static List<List<DraftPack>> deepCopy(List<List<DraftPack>> rounds) {
        if (rounds == null) {
            return List.of();
        }
        List<List<DraftPack>> copy = new ArrayList<>(rounds.size());
        for (List<DraftPack> round : rounds) {
            copy.add(List.copyOf(round));
        }
        return List.copyOf(copy);
    }
}
