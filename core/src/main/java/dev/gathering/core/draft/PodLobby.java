package dev.gathering.core.draft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * A draft or sealed event before its packs are opened: who is hosting, what they decided, and
 * every pack put in so far and by whom.
 * <p>What the packs are waiting for is decided here - who still owes a pack, whether this pack
 * is one that counts, whether everything is in - so the table, the lobby screen and the tests
 * all ask one set of rules. None of it decides anything about play; it is a sign-up sheet with
 * the packs clipped to it.
 * <p>Who is taking part is not stored. It is whoever is sitting at the tables when the host
 * starts, exactly as a cube draft has always been, so the rules here are asked with the seated
 * players and a player who stands up simply stops owing anything.
 *
 * @param host     who created the event, and who sponsors it if anybody does
 * @param settings what the host decided
 * @param entries  every pack put in, in the order it went in
 */
public record PodLobby(UUID host, PodSettings settings, List<Entry> entries) {

    /**
     * One pack, and who put it in.
     *
     * @param contributor who it is held for; null only for a pack the server made
     */
    public record Entry(UUID contributor, PackRef pack) {

        public Entry {
            if (pack == null) {
                throw new IllegalArgumentException("An entry needs a pack");
            }
        }
    }

    /** A pack, described the way a pack item describes itself. */
    public record PackRef(String setCode, String kind, String color) {

        public PackRef {
            setCode = setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT);
            kind = kind == null ? "" : kind;
            color = color == null ? "" : color;
        }
    }

    public PodLobby {
        if (host == null) {
            throw new IllegalArgumentException("An event needs a host");
        }
        settings = settings == null ? PodSettings.usual(PodSettings.Kind.DRAFT) : settings;
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static PodLobby open(UUID host, PodSettings settings) {
        return new PodLobby(host, settings, List.of());
    }

    /** The packs this player has put in, in order. */
    public List<Entry> entriesOf(UUID player) {
        List<Entry> theirs = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.contributor() != null && entry.contributor().equals(player)) {
                theirs.add(entry);
            }
        }
        return List.copyOf(theirs);
    }

    /** How many more packs this player has to put in, with these players seated. */
    public int stillOwedBy(UUID player, List<UUID> seated) {
        return switch (settings.source()) {
            case EACH_BRINGS -> seated.contains(player)
                    ? Math.max(0, settings.packsEach() - entriesOf(player).size()) : 0;
            case SPONSORED -> host.equals(player)
                    ? Math.max(0, seated.size() * settings.packsEach() - entriesOf(player).size())
                    : 0;
            case GENERATED -> 0;
        };
    }

    /**
     * Why this pack cannot go in, or empty when it can. A translation key.
     * <p>Refused before the pack leaves the player's hand, so nothing is ever held that the
     * event would only have to hand back.
     */
    public Optional<String> refusal(UUID player, PackRef pack, List<UUID> seated) {
        switch (settings.source()) {
            case GENERATED -> {
                return Optional.of("message.gathering.pod.no_packs_wanted");
            }
            case SPONSORED -> {
                if (!host.equals(player)) {
                    return Optional.of("message.gathering.pod.host_brings_the_packs");
                }
            }
            case EACH_BRINGS -> {
                if (!seated.contains(player)) {
                    return Optional.of("message.gathering.pod.sit_down_first");
                }
            }
        }
        if (stillOwedBy(player, seated) <= 0) {
            return Optional.of("message.gathering.pod.enough_packs");
        }
        return counts(player, pack, seated)
                ? Optional.empty()
                : Optional.of("message.gathering.pod.wrong_set");
    }

    /** Whether this pack is one the event can use, before counting how many are already in. */
    private boolean counts(UUID player, PackRef pack, List<UUID> seated) {
        PodSettings.SetRule sets = settings.sets();
        if (settings.source() == PodSettings.Source.EACH_BRINGS) {
            // A player's own packs go in in order, pack one first: the next one is the next
            // pack they open.
            return sets.allows(entriesOf(player).size(), pack.setCode());
        }
        if (!sets.perPack()) {
            return sets.allows(0, pack.setCode());
        }
        // A sponsor puts in a whole round of each set. A set named for two rounds wants two
        // rounds' worth, so what is counted is packs of that set against every round that
        // names it - counting against one round refused the second round's first pack.
        int rounds = 0;
        for (String named : sets.sets()) {
            if (named.equals(pack.setCode())) {
                rounds++;
            }
        }
        int already = 0;
        for (Entry entry : entries) {
            if (entry.pack().setCode().equals(pack.setCode())) {
                already++;
            }
        }
        return already < rounds * Math.max(1, seated.size());
    }

    /** The lobby with one more pack in it. Refuses what {@link #refusal} refuses. */
    public PodLobby with(UUID player, PackRef pack, List<UUID> seated) {
        String refused = refusal(player, pack, seated).orElse(null);
        if (refused != null) {
            throw new IllegalArgumentException(refused);
        }
        List<Entry> more = new ArrayList<>(entries);
        more.add(new Entry(player, pack));
        return new PodLobby(host, settings, more);
    }

    /** The lobby with this player's packs taken back out, for when they leave it. */
    public PodLobby without(UUID player) {
        List<Entry> kept = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.contributor() == null || !entry.contributor().equals(player)) {
                kept.add(entry);
            }
        }
        return new PodLobby(host, settings, kept);
    }

    /** Why the host cannot start yet with these players seated, or empty. A translation key. */
    public Optional<String> notReady(List<UUID> seated) {
        if (settings.problem().isPresent()) {
            return settings.problem();
        }
        if (seated.size() < settings.fewestPlayers()) {
            return Optional.of("message.gathering.pod.too_few");
        }
        if (seated.size() > settings.mostPlayers()) {
            return Optional.of("message.gathering.pod.too_many");
        }
        for (UUID player : seated) {
            if (stillOwedBy(player, seated) > 0) {
                return Optional.of("message.gathering.pod.waiting_for_packs");
            }
        }
        if (settings.source() == PodSettings.Source.SPONSORED && stillOwedBy(host, seated) > 0) {
            return Optional.of("message.gathering.pod.waiting_for_packs");
        }
        return plan(seated).isPresent()
                ? Optional.empty()
                : Optional.of("message.gathering.pod.waiting_for_packs");
    }

    /**
     * Which pack each player opens, and what is left over.
     * <p>{@code bySeat.get(seat).get(n)} is the <i>n</i>-th pack the player in that seat opens:
     * their round-<i>n</i> pack in a draft, their <i>n</i>-th pack in sealed. Leftovers are
     * packs put in by somebody who is no longer seated, or a sponsor's spares after players
     * stood up - handed back unopened.
     * <p>Empty when the packs in are not enough to go round, which {@link #notReady} reports.
     */
    public Optional<Plan> plan(List<UUID> seated) {
        List<List<Entry>> bySeat = new ArrayList<>();
        List<Entry> left = new ArrayList<>(entries);
        PodSettings.SetRule sets = settings.sets();
        switch (settings.source()) {
            case GENERATED -> {
                for (int seat = 0; seat < seated.size(); seat++) {
                    List<Entry> packs = new ArrayList<>();
                    for (int index = 0; index < settings.packsEach(); index++) {
                        packs.add(new Entry(null, new PackRef(sets.setFor(index).orElse(""), "", "")));
                    }
                    bySeat.add(List.copyOf(packs));
                }
            }
            case EACH_BRINGS -> {
                for (UUID player : seated) {
                    List<Entry> theirs = entriesOf(player);
                    if (theirs.size() < settings.packsEach()) {
                        return Optional.empty();
                    }
                    List<Entry> opened = theirs.subList(0, settings.packsEach());
                    bySeat.add(List.copyOf(opened));
                    for (Entry entry : opened) {
                        left.remove(entry);
                    }
                }
            }
            case SPONSORED -> {
                for (int seat = 0; seat < seated.size(); seat++) {
                    bySeat.add(new ArrayList<>());
                }
                for (int index = 0; index < settings.packsEach(); index++) {
                    for (int seat = 0; seat < seated.size(); seat++) {
                        Entry found = null;
                        for (Entry entry : left) {
                            if (sets.allows(index, entry.pack().setCode())) {
                                found = entry;
                                break;
                            }
                        }
                        if (found == null) {
                            return Optional.empty();
                        }
                        left.remove(found);
                        bySeat.get(seat).add(found);
                    }
                }
                bySeat.replaceAll(List::copyOf);
            }
        }
        return Optional.of(new Plan(List.copyOf(bySeat), List.copyOf(left)));
    }

    /**
     * Which pack each seat opens, and what goes back unopened.
     *
     * @param bySeat per seat, the packs that seat opens, in order
     * @param unused packs nobody opens, to hand back to whoever put them in
     */
    public record Plan(List<List<Entry>> bySeat, List<Entry> unused) {
    }
}
