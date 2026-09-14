package dev.gathering.core.tournament;

import dev.gathering.core.draft.PodSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A tournament as bytes, for the world save.
 * <p>Versioned from the start, and read defensively: a tournament is a record other players
 * depend on, and one that does not add up is refused with the reason rather than loaded half.
 */
public final class TournamentCodec {

    /** Two, for the pick clock in pack settings. One is still read, with no clock. */
    public static final int VERSION = 2;

    private static final int MOST = 4096;

    private TournamentCodec() {
    }

    public static byte[] write(Tournament tournament) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            uuid(out, tournament.id());
            out.writeUTF(tournament.name());
            uuid(out, tournament.host());
            settings(out, tournament.settings());
            out.writeUTF(tournament.phase().name());
            out.writeInt(tournament.plannedRounds());
            out.writeInt(tournament.entrants().size());
            for (Entrant entrant : tournament.entrants()) {
                uuid(out, entrant.id());
                out.writeUTF(entrant.name());
                out.writeDouble(entrant.seed());
                out.writeInt(entrant.droppedAfter());
            }
            uuids(out, tournament.checkedIn());
            uuids(out, tournament.ready());
            out.writeInt(tournament.rounds().size());
            for (Round round : tournament.rounds()) {
                out.writeInt(round.number());
                out.writeBoolean(round.elimination());
                out.writeBoolean(round.timeCalled());
                out.writeInt(round.pairings().size());
                for (Pairing pairing : round.pairings()) {
                    out.writeInt(pairing.table());
                    uuid(out, pairing.a());
                    out.writeBoolean(pairing.b() != null);
                    if (pairing.b() != null) {
                        uuid(out, pairing.b());
                    }
                    result(out, pairing.reportA());
                    result(out, pairing.reportB());
                    result(out, pairing.result());
                    out.writeInt(pairing.turnsAfterTime());
                }
            }
        } catch (IOException impossible) {
            throw new IllegalStateException("Writing to memory failed", impossible);
        }
        return bytes.toByteArray();
    }

    public static Tournament read(byte[] written) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(written))) {
            int version = in.readInt();
            if (version != 1 && version != VERSION) {
                throw new IOException("A tournament is version " + version + ", this reads 1 and " + VERSION);
            }
            UUID id = uuid(in);
            String name = in.readUTF();
            UUID host = uuid(in);
            EventSettings settings = settings(in, version);
            Tournament.Phase phase = Tournament.Phase.valueOf(in.readUTF());
            int planned = in.readInt();
            int count = bounded(in.readInt());
            List<Entrant> entrants = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                entrants.add(new Entrant(uuid(in), in.readUTF(), in.readDouble(), in.readInt()));
            }
            Set<UUID> checkedIn = uuids(in);
            Set<UUID> ready = uuids(in);
            int roundCount = bounded(in.readInt());
            List<Round> rounds = new ArrayList<>(roundCount);
            for (int index = 0; index < roundCount; index++) {
                int number = in.readInt();
                boolean elimination = in.readBoolean();
                boolean timeCalled = in.readBoolean();
                int pairingCount = bounded(in.readInt());
                List<Pairing> pairings = new ArrayList<>(pairingCount);
                for (int each = 0; each < pairingCount; each++) {
                    int table = in.readInt();
                    UUID a = uuid(in);
                    UUID b = in.readBoolean() ? uuid(in) : null;
                    pairings.add(new Pairing(table, a, b, result(in), result(in), result(in), in.readInt()));
                }
                rounds.add(new Round(number, elimination, pairings, timeCalled));
            }
            return new Tournament(id, name, host, settings, phase, entrants, checkedIn, ready, rounds, planned);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("A saved tournament does not add up: " + malformed.getMessage(), malformed);
        }
    }

    private static void settings(DataOutputStream out, EventSettings settings) throws IOException {
        out.writeUTF(settings.kind().name());
        out.writeUTF(settings.formatId());
        out.writeInt(settings.bestOf());
        out.writeInt(settings.roundMinutes());
        out.writeInt(settings.buildMinutes());
        out.writeInt(settings.extraTurns());
        out.writeInt(settings.rounds());
        out.writeInt(settings.topCut());
        out.writeUTF(settings.decks().name());
        out.writeBoolean(settings.largeEvent());
        PodSettings pod = settings.pod();
        out.writeBoolean(pod != null);
        if (pod != null) {
            out.writeUTF(pod.kind().name());
            out.writeUTF(pod.source().name());
            out.writeUTF(pod.sets().mode().name());
            out.writeInt(pod.sets().sets().size());
            for (String set : pod.sets().sets()) {
                out.writeUTF(set);
            }
            out.writeInt(pod.packsEach());
            out.writeInt(pod.picksPerTurn());
            out.writeUTF(pod.cardsGo().name());
            out.writeInt(pod.pickSeconds());
        }
    }

    private static EventSettings settings(DataInputStream in, int version) throws IOException {
        EventSettings.Kind kind = EventSettings.Kind.valueOf(in.readUTF());
        String format = in.readUTF();
        int bestOf = in.readInt();
        int roundMinutes = in.readInt();
        int buildMinutes = in.readInt();
        int extraTurns = in.readInt();
        int rounds = in.readInt();
        int topCut = in.readInt();
        EventSettings.DeckRegistration decks = EventSettings.DeckRegistration.valueOf(in.readUTF());
        boolean large = in.readBoolean();
        PodSettings pod = null;
        if (in.readBoolean()) {
            PodSettings.Kind podKind = PodSettings.Kind.valueOf(in.readUTF());
            PodSettings.Source source = PodSettings.Source.valueOf(in.readUTF());
            PodSettings.SetRule.Mode mode = PodSettings.SetRule.Mode.valueOf(in.readUTF());
            int sets = bounded(in.readInt());
            List<String> named = new ArrayList<>(sets);
            for (int index = 0; index < sets; index++) {
                named.add(in.readUTF());
            }
            int packsEach = in.readInt();
            int picks = in.readInt();
            PodSettings.CardsGo cardsGo = PodSettings.CardsGo.valueOf(in.readUTF());
            int pickSeconds = version >= 2 ? in.readInt() : 0;
            pod = new PodSettings(podKind, source, new PodSettings.SetRule(mode, named), packsEach, picks, cardsGo, pickSeconds);
        }
        return new EventSettings(kind, format, pod, bestOf, roundMinutes, buildMinutes, extraTurns, rounds, topCut,
                decks, large);
    }

    private static void result(DataOutputStream out, MatchResult result) throws IOException {
        out.writeBoolean(result != null);
        if (result != null) {
            out.writeInt(result.winsA());
            out.writeInt(result.winsB());
            out.writeInt(result.draws());
        }
    }

    private static MatchResult result(DataInputStream in) throws IOException {
        return in.readBoolean() ? new MatchResult(in.readInt(), in.readInt(), in.readInt()) : null;
    }

    private static void uuids(DataOutputStream out, Set<UUID> ids) throws IOException {
        out.writeInt(ids.size());
        for (UUID id : ids) {
            uuid(out, id);
        }
    }

    private static Set<UUID> uuids(DataInputStream in) throws IOException {
        int count = bounded(in.readInt());
        Set<UUID> ids = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            ids.add(uuid(in));
        }
        return ids;
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID uuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static int bounded(int count) throws IOException {
        if (count < 0 || count > MOST) {
            throw new IOException("A count out of range: " + count);
        }
        return count;
    }
}
