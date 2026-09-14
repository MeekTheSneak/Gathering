package dev.gathering.core.draft;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What a host decides before anybody puts a pack in: what kind of event, where the packs come
 * from, which packs count, how many, and who ends up with the cards.
 * <p>Decided once, when the pod is created, and never changed after a pack has been put in.
 * Every one of these is a promise to the people contributing - "you get your cards back",
 * "only this set" - and a promise that could be edited after somebody had paid into it is not
 * one.
 * <p>Pure, so every combination the create screen can produce is checked here rather than
 * discovered at the table.
 *
 * @param kind         a draft passes packs round the ring; sealed hands each player their own
 * @param source       who puts the packs in
 * @param sets         which packs count
 * @param packsEach    how many packs each player drafts or opens
 * @param picksPerTurn 1 or 2, or 0 for the pod size to decide - see {@link DraftRules}
 * @param cardsGo      who ends up with the cards when it is over
 * @param pickSeconds  how long a drafter has to pick before the first cards in their pack are
 *                     picked for them, or 0 for no clock
 */
public record PodSettings(
        Kind kind, Source source, SetRule sets, int packsEach, int picksPerTurn, CardsGo cardsGo, int pickSeconds) {

    /** Settings with no pick clock, as every event had before the clock existed. */
    public PodSettings(Kind kind, Source source, SetRule sets, int packsEach, int picksPerTurn, CardsGo cardsGo) {
        this(kind, source, sets, packsEach, picksPerTurn, cardsGo, 0);
    }

    /** The longest a pick clock may be set to. */
    public static final int LONGEST_PICK_SECONDS = 300;

    /** A draft or a sealed event. */
    public enum Kind {
        DRAFT, SEALED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Who puts the packs in. */
    public enum Source {
        /** Every player puts in their own. */
        EACH_BRINGS,
        /** The host puts in every pack, for everybody. */
        SPONSORED,
        /** Nobody: the server makes the packs, where the server allows it. */
        GENERATED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Who ends up with the cards. */
    public enum CardsGo {
        /** Each player keeps what they drafted or opened. */
        PLAYERS_KEEP,
        /** Every card goes to the host who sponsored the packs. */
        TO_SPONSOR,
        /** Each card goes back to whoever put in the pack it came out of. */
        TO_CONTRIBUTORS;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The most packs anybody drafts or opens. Twelve is two sealed events' worth. */
    public static final int MOST_PACKS_EACH = 12;

    /** What a draft is, with nobody saying otherwise. */
    public static final int USUAL_DRAFT_PACKS = 3;

    /** What sealed is, with nobody saying otherwise. */
    public static final int USUAL_SEALED_PACKS = 6;

    public PodSettings {
        kind = kind == null ? Kind.DRAFT : kind;
        source = source == null ? Source.EACH_BRINGS : source;
        sets = sets == null ? SetRule.ANY : sets;
        cardsGo = cardsGo == null ? CardsGo.PLAYERS_KEEP : cardsGo;
    }

    /** A draft or sealed event as it runs when the host changes nothing. */
    public static PodSettings usual(Kind kind) {
        return new PodSettings(kind, Source.EACH_BRINGS, SetRule.ANY,
                kind == Kind.SEALED ? USUAL_SEALED_PACKS : USUAL_DRAFT_PACKS, 0,
                CardsGo.PLAYERS_KEEP);
    }

    /**
     * Why these settings cannot make an event, or empty when they can.
     * <p>Returned as a translation key, because the answer is shown on the create screen and
     * the create screen is the only place the combination can be put right.
     */
    public Optional<String> problem() {
        if (packsEach < 1 || packsEach > MOST_PACKS_EACH) {
            return Optional.of("message.gathering.pod.packs_each");
        }
        if (picksPerTurn < 0 || picksPerTurn > 2) {
            return Optional.of("message.gathering.pod.picks_per_turn");
        }
        if (pickSeconds < 0 || pickSeconds > LONGEST_PICK_SECONDS || (pickSeconds > 0 && kind == Kind.SEALED)) {
            return Optional.of("message.gathering.pod.pick_clock");
        }
        if (kind == Kind.SEALED && picksPerTurn != 0) {
            // Nothing is picked in sealed. A setting that does nothing is a setting somebody
            // spends a minute wondering about.
            return Optional.of("message.gathering.pod.sealed_has_no_picks");
        }
        if (cardsGo == CardsGo.TO_SPONSOR && source != Source.SPONSORED) {
            // Nobody sponsored anything, so there is nobody for the cards to go back to.
            return Optional.of("message.gathering.pod.no_sponsor");
        }
        if (source == Source.GENERATED && cardsGo != CardsGo.PLAYERS_KEEP) {
            // Nobody put these packs in, so there is nobody to give them back to.
            return Optional.of("message.gathering.pod.generated_has_no_owner");
        }
        if (sets.perPack() && sets.sets().size() != packsEach) {
            return Optional.of("message.gathering.pod.set_per_pack");
        }
        if (source == Source.GENERATED && sets.isAny()) {
            // A server making packs has to be told what to make them of.
            return Optional.of("message.gathering.pod.generated_needs_a_set");
        }
        return Optional.empty();
    }

    /**
     * How many cards each drafter takes at a time in a pod of this size.
     * <p>The host's number if they chose one, and otherwise the rule a pod size has always
     * decided: two at a time under six drafters, one from six up.
     */
    public int picksFor(int drafters) {
        return picksPerTurn == 0 ? DraftRules.picksPerTurn(drafters) : picksPerTurn;
    }

    /** The fewest players this kind of event can run with. */
    public int fewestPlayers() {
        // Sealed is one person opening their own packs, which two or seven more people doing
        // the same beside them does not change. A draft needs a ring to pass round.
        return kind == Kind.SEALED ? 1 : DraftRules.SMALLEST_POD;
    }

    /** The most. A cluster seats eight, and so does a pod. */
    public int mostPlayers() {
        return DraftRules.LARGEST_POD;
    }

    /**
     * Which packs count.
     *
     * @param mode ANY, ONE_SET (every pack from {@code sets.get(0)}) or PER_PACK (pack
     *             <i>n</i> from {@code sets.get(n)})
     */
    public record SetRule(Mode mode, List<String> sets) {

        public enum Mode {
            ANY, ONE_SET, PER_PACK
        }

        public static final SetRule ANY = new SetRule(Mode.ANY, List.of());

        public SetRule {
            mode = mode == null ? Mode.ANY : mode;
            List<String> cleaned = new java.util.ArrayList<>();
            if (sets != null) {
                for (String set : sets) {
                    cleaned.add(set == null ? "" : set.trim().toLowerCase(Locale.ROOT));
                }
            }
            sets = List.copyOf(cleaned);
            if (mode == Mode.ANY && !sets.isEmpty()) {
                throw new IllegalArgumentException("Any pack counts, so no set is named");
            }
            if (mode == Mode.ONE_SET && (sets.size() != 1 || sets.get(0).isEmpty())) {
                throw new IllegalArgumentException("One set means one set named");
            }
            if (mode == Mode.PER_PACK && (sets.isEmpty() || sets.contains(""))) {
                throw new IllegalArgumentException("A set per pack means every pack's set named");
            }
        }

        public static SetRule oneSet(String set) {
            return new SetRule(Mode.ONE_SET, List.of(set));
        }

        public static SetRule perPack(List<String> sets) {
            return new SetRule(Mode.PER_PACK, sets);
        }

        public boolean isAny() {
            return mode == Mode.ANY;
        }

        public boolean perPack() {
            return mode == Mode.PER_PACK;
        }

        /** The set pack number {@code index} (from zero) has to be, or empty for any. */
        public Optional<String> setFor(int index) {
            return switch (mode) {
                case ANY -> Optional.empty();
                case ONE_SET -> Optional.of(sets.get(0));
                case PER_PACK -> index >= 0 && index < sets.size()
                        ? Optional.of(sets.get(index)) : Optional.empty();
            };
        }

        /** Whether a pack of this set may be pack number {@code index}. */
        public boolean allows(int index, String set) {
            String wanted = setFor(index).orElse(null);
            return wanted == null
                    || (set != null && wanted.equals(set.trim().toLowerCase(Locale.ROOT)));
        }
    }
}
