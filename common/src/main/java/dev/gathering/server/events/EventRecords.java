package dev.gathering.server.events;

import dev.gathering.core.tournament.Entrant;
import dev.gathering.core.tournament.MatchResult;
import dev.gathering.core.tournament.Pairing;
import dev.gathering.core.tournament.Round;
import dev.gathering.core.tournament.Tournament;
import dev.gathering.server.ServerRun;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every player's tournament record on this server, and the private rating events are seeded by.
 * <p>The record - matches and games won, lost and drawn, events played and won - is public: any
 * player may look anybody up. The rating is not. It is read by the pairing of round one and by
 * admins, and nowhere else, so nobody can see a seed to aim at.
 * <p>What keeps the rating honest, as the owner decided:
 * <ul>
 * <li>Only a finished event with at least {@value #RATED_MIN_PLAYERS} players moves ratings. A
 * called-off or tiny event counts for nothing.</li>
 * <li>A host runs one unfinished event at a time.</li>
 * <li>The same two players: only their first {@value #PAIR_LIMIT} meetings in any seven days
 * move ratings, so two friends cannot feed each other results.</li>
 * <li>A result with no game played at the table stays in the standings and moves no rating.</li>
 * <li>Admins can void an event's effect on ratings, leave a player out of ratings, and mark an
 * event official. An event nobody marked official moves ratings at half weight.</li>
 * </ul>
 * <p>Losing on purpose to lower a seed needs no rule here: round one folds the seeds, so a player
 * who drags theirs down is paired against a top seed.
 */
public final class EventRecords {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");
    private static final String FOLDER = "gathering-events";
    private static final String FILE = "records.dat";

    public static final double STARTING_RATING = 1500.0;
    /** The default; a server's own number is in its config, events.rated_min_players. */
    public static final int RATED_MIN_PLAYERS = 6;
    public static final int PAIR_LIMIT = 3;
    public static final long PAIR_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    /** One player's record. Mutable; server thread only. */
    public static final class Record {
        String name = "";
        double rating = STARTING_RATING;
        int ratedMatches;
        int matchWins;
        int matchLosses;
        int matchDraws;
        int gameWins;
        int gameLosses;
        int gameDraws;
        int eventsPlayed;
        int eventsWon;
        boolean excluded;

        public String name() {
            return name;
        }

        public int matchWins() {
            return matchWins;
        }

        public int matchLosses() {
            return matchLosses;
        }

        public int matchDraws() {
            return matchDraws;
        }

        public int gameWins() {
            return gameWins;
        }

        public int gameLosses() {
            return gameLosses;
        }

        public int gameDraws() {
            return gameDraws;
        }

        public int eventsPlayed() {
            return eventsPlayed;
        }

        public int eventsWon() {
            return eventsWon;
        }

        /** Admins only. */
        public double rating() {
            return rating;
        }

        public boolean excluded() {
            return excluded;
        }

        /** Matches won, as a share of matches played; zero with none played. */
        public double matchWinRate() {
            int played = matchWins + matchLosses + matchDraws;
            return played == 0 ? 0 : (double) matchWins / played;
        }
    }

    private static Map<UUID, Record> records;
    /** When each pair of players last met in a rated match, most recent last. */
    private static Map<String, List<Long>> meetings;
    /** Rating changes each event made, so an admin can void them. */
    private static Map<UUID, Map<UUID, Double>> effects;
    private static Set<UUID> official;
    /** When each host last created a tournament, for the cooldown. */
    private static Map<UUID, Long> lastHosted;

    private EventRecords() {
    }

    public static void clear() {
        records = null;
        meetings = null;
        effects = null;
        official = null;
        lastHosted = null;
    }

    private static void load() {
        if (records != null) {
            return;
        }
        records = new LinkedHashMap<>();
        meetings = new LinkedHashMap<>();
        effects = new LinkedHashMap<>();
        official = new java.util.LinkedHashSet<>();
        lastHosted = new LinkedHashMap<>();
        Path file = path().orElse(null);
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
            for (int index = 0; index < players.size(); index++) {
                CompoundTag entry = players.getCompound(index);
                Record record = new Record();
                record.name = entry.getString("name");
                record.rating = entry.getDouble("rating");
                record.ratedMatches = entry.getInt("rated");
                record.matchWins = entry.getInt("mw");
                record.matchLosses = entry.getInt("ml");
                record.matchDraws = entry.getInt("md");
                record.gameWins = entry.getInt("gw");
                record.gameLosses = entry.getInt("gl");
                record.gameDraws = entry.getInt("gd");
                record.eventsPlayed = entry.getInt("played");
                record.eventsWon = entry.getInt("won");
                record.excluded = entry.getBoolean("excluded");
                records.put(entry.getUUID("id"), record);
            }
            CompoundTag pairs = tag.getCompound("meetings");
            for (String key : pairs.getAllKeys()) {
                List<Long> times = new ArrayList<>();
                for (long time : pairs.getLongArray(key)) {
                    times.add(time);
                }
                meetings.put(key, times);
            }
            ListTag events = tag.getList("effects", Tag.TAG_COMPOUND);
            for (int index = 0; index < events.size(); index++) {
                CompoundTag entry = events.getCompound(index);
                Map<UUID, Double> deltas = new LinkedHashMap<>();
                CompoundTag changes = entry.getCompound("deltas");
                for (String key : changes.getAllKeys()) {
                    deltas.put(UUID.fromString(key), changes.getDouble(key));
                }
                effects.put(entry.getUUID("event"), deltas);
            }
            CompoundTag hosts = tag.getCompound("hosted");
            for (String key : hosts.getAllKeys()) {
                lastHosted.put(UUID.fromString(key), hosts.getLong(key));
            }
            for (long[] packed : new long[][] {tag.getLongArray("official")}) {
                for (int index = 0; index + 1 < packed.length; index += 2) {
                    official.add(new UUID(packed[index], packed[index + 1]));
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            LOGGER.error("The tournament records will not load: {}", unreadable.toString());
        }
    }

    private static void save() {
        Path file = path().orElse(null);
        if (file == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        ListTag players = new ListTag();
        records.forEach((id, record) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putString("name", record.name);
            entry.putDouble("rating", record.rating);
            entry.putInt("rated", record.ratedMatches);
            entry.putInt("mw", record.matchWins);
            entry.putInt("ml", record.matchLosses);
            entry.putInt("md", record.matchDraws);
            entry.putInt("gw", record.gameWins);
            entry.putInt("gl", record.gameLosses);
            entry.putInt("gd", record.gameDraws);
            entry.putInt("played", record.eventsPlayed);
            entry.putInt("won", record.eventsWon);
            entry.putBoolean("excluded", record.excluded);
            players.add(entry);
        });
        tag.put("players", players);
        CompoundTag pairs = new CompoundTag();
        meetings.forEach((key, times) -> pairs.putLongArray(key, times.stream().mapToLong(Long::longValue).toArray()));
        tag.put("meetings", pairs);
        ListTag events = new ListTag();
        effects.forEach((event, deltas) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("event", event);
            CompoundTag changes = new CompoundTag();
            deltas.forEach((player, delta) -> changes.putDouble(player.toString(), delta));
            entry.put("deltas", changes);
            events.add(entry);
        });
        tag.put("effects", events);
        long[] packed = new long[official.size() * 2];
        int at = 0;
        for (UUID id : official) {
            packed[at++] = id.getMostSignificantBits();
            packed[at++] = id.getLeastSignificantBits();
        }
        tag.putLongArray("official", packed);
        CompoundTag hosts = new CompoundTag();
        lastHosted.forEach((host, time) -> hosts.putLong(host.toString(), time));
        tag.put("hosted", hosts);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(FILE + ".tmp");
            NbtIo.writeCompressed(tag, temporary);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failed) {
            LOGGER.error("The tournament records could not be saved: {}", failed.toString());
        }
    }

    private static Optional<Path> path() {
        return ServerRun.inSave(FOLDER).map(folder -> folder.resolve(FILE));
    }

    // ------------------------------------------------------------------ reading

    /** A player's private rating, for seeding. Never shown to players. */
    public static double seedOf(UUID player) {
        load();
        Record record = records.get(player);
        return record == null ? STARTING_RATING : record.rating;
    }

    public static Optional<Record> recordOf(UUID player) {
        load();
        return Optional.ofNullable(records.get(player));
    }

    /** Looks a player up by name, for the record command. */
    public static Optional<Map.Entry<UUID, Record>> byName(String name) {
        load();
        return records.entrySet().stream().filter(entry -> entry.getValue().name.equalsIgnoreCase(name)).findFirst();
    }

    public static boolean isOfficial(UUID event) {
        load();
        return official.contains(event);
    }

    // ------------------------------------------------------------------ hosting

    /** Why this player may not host another event right now, if they may not. */
    public static Optional<String> whyNotHost(UUID host) {
        return whyNotHost(host, System.currentTimeMillis());
    }

    public static Optional<String> whyNotHost(UUID host, long now) {
        load();
        boolean running = Events.all().stream()
                .anyMatch(state -> !state.tournament.isOver() && state.tournament.host().equals(host));
        if (running) {
            return Optional.of("message.gathering.event.hosting_one");
        }
        long cooldown = dev.gathering.service.ServerSettings.get().events().hostCooldownMinutes() * 60_000L;
        Long last = lastHosted.get(host);
        return last != null && now - last < cooldown
                ? Optional.of("message.gathering.event.host_cooldown")
                : Optional.empty();
    }

    static void hosted(UUID host) {
        load();
        lastHosted.put(host, System.currentTimeMillis());
        save();
    }

    /** The fewest players a finished tournament needs to move ratings, from the server's config. */
    static int ratedMinPlayers() {
        return dev.gathering.service.ServerSettings.get().events().ratedMinPlayers();
    }

    // ------------------------------------------------------------------ recording

    /**
     * The same, knowing which matches had a game played at their table.
     *
     * @param playedAtTable "round:table" for every match a game was played at, which is what a
     *                      rated result needs. A result with no game behind
     *                      it still counts in the record, and never moves a rating: a result
     *                      two players only typed in is a result anybody could arrange
     */
    public static void finished(Tournament tournament, Set<String> playedAtTable, long now) {
        load();
        if (tournament.phase() != Tournament.Phase.FINISHED || effects.containsKey(tournament.id())) {
            return;
        }
        for (Entrant entrant : tournament.entrants()) {
            Record record = records.computeIfAbsent(entrant.id(), ignored -> new Record());
            record.name = entrant.name();
            record.eventsPlayed++;
        }
        List<UUID> places = tournament.finalPlaces();
        if (!places.isEmpty()) {
            records.get(places.get(0)).eventsWon++;
        }
        boolean rated = tournament.entrants().size() >= ratedMinPlayers();
        double weight = official.contains(tournament.id()) ? 1.0 : 0.5;
        Map<UUID, Double> deltas = new LinkedHashMap<>();
        for (Round round : tournament.rounds()) {
            for (Pairing pairing : round.pairings()) {
                if (!pairing.isConfirmed() || pairing.isBye()) {
                    continue;
                }
                MatchResult result = pairing.result();
                Record a = records.get(pairing.a());
                Record b = records.get(pairing.b());
                if (a == null || b == null) {
                    continue;
                }
                count(a, result);
                count(b, result.flipped());
                if (!rated || a.excluded || b.excluded) {
                    continue;
                }
                if (!playedAtTable.contains(round.number() + ":" + pairing.table())) {
                    continue;
                }
                if (!meetingCounts(pairing.a(), pairing.b(), now)) {
                    continue;
                }
                double score = result.firstWon() ? 1.0 : result.secondWon() ? 0.0 : 0.5;
                double expected = 1.0 / (1.0 + Math.pow(10.0, (b.rating - a.rating) / 400.0));
                double changeA = factor(a) * weight * (score - expected);
                double changeB = factor(b) * weight * ((1.0 - score) - (1.0 - expected));
                a.rating += changeA;
                b.rating += changeB;
                a.ratedMatches++;
                b.ratedMatches++;
                deltas.merge(pairing.a(), changeA, Double::sum);
                deltas.merge(pairing.b(), changeB, Double::sum);
            }
        }
        effects.put(tournament.id(), deltas);
        save();
    }

    private static void count(Record record, MatchResult result) {
        if (result.firstWon()) {
            record.matchWins++;
        } else if (result.secondWon()) {
            record.matchLosses++;
        } else {
            record.matchDraws++;
        }
        record.gameWins += result.winsA();
        record.gameLosses += result.winsB();
        record.gameDraws += result.draws();
    }

    /** Provisional players move faster, so a new player's rating finds its level. */
    private static double factor(Record record) {
        return record.ratedMatches < 10 ? 40.0 : 20.0;
    }

    /** Whether this meeting of two players may move ratings, recording it if so. */
    private static boolean meetingCounts(UUID a, UUID b, long now) {
        String key = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
        List<Long> times = meetings.computeIfAbsent(key, ignored -> new ArrayList<>());
        times.removeIf(time -> now - time >= PAIR_WINDOW_MILLIS);
        if (times.size() >= PAIR_LIMIT) {
            return false;
        }
        times.add(now);
        return true;
    }

    // ------------------------------------------------------------------ admin

    /** Takes back every rating change an event made. Its records of wins and losses stay. */
    public static boolean voidRatings(UUID event) {
        load();
        Map<UUID, Double> deltas = effects.get(event);
        if (deltas == null || deltas.isEmpty()) {
            return false;
        }
        deltas.forEach((player, delta) -> {
            Record record = records.get(player);
            if (record != null) {
                record.rating -= delta;
            }
        });
        effects.put(event, Map.of());
        save();
        return true;
    }

    public static void setExcluded(UUID player, String name, boolean excluded) {
        load();
        Record record = records.computeIfAbsent(player, ignored -> new Record());
        if (name != null && !name.isBlank()) {
            record.name = name;
        }
        record.excluded = excluded;
        save();
    }

    public static void setOfficial(UUID event, boolean isOfficial) {
        load();
        if (isOfficial) {
            official.add(event);
        } else {
            official.remove(event);
        }
        save();
    }

    /** For the in-world tests: a host having hosted at this moment. */
    public static void hostedAtForTesting(UUID host, long when) {
        load();
        lastHosted.put(host, when);
    }

    /** For the in-world tests: a record's rating set directly. */
    public static void setRatingForTesting(UUID player, double rating) {
        load();
        records.computeIfAbsent(player, ignored -> new Record()).rating = rating;
    }
}
