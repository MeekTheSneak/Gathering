package dev.gathering.core.draft;

import dev.gathering.core.card.CardIdentity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a draft or sealed event's packs held, and whose packs they were.
 * <p>Kept from the moment the packs are opened until the cards are handed out, because the
 * handing out needs it: cards going back to whoever put each pack in have to be counted
 * against what each pack actually held, and a table broken halfway through a draft has to be
 * able to give every contributor back what their own packs held. A draft pod alone knows only
 * the picks, and a pick says nothing about whose pack the card came out of.
 * <p>Saved with the pod. Pure, with its own bytes, so it round-trips in a test.
 *
 * @param host         who hosted, and who sponsored if anybody did
 * @param cardsGo      who ends up with the cards
 * @param seated       the players, in seat order
 * @param contributors per seat, per pack, who put that pack in; null for a pack the server made
 * @param opened       per seat, per pack, what came out of it
 * @param podName      what the event calls itself on a pool, never the shuffle seed
 * @param pickSeconds  the pick clock the host set, or 0
 */
public record PodRecord(
        UUID host, PodSettings.CardsGo cardsGo, List<UUID> seated,
        List<List<UUID>> contributors, List<List<List<CardIdentity>>> opened, String podName, int pickSeconds) {

    /** A record with no pick clock. */
    public PodRecord(UUID host, PodSettings.CardsGo cardsGo, List<UUID> seated,
            List<List<UUID>> contributors, List<List<List<CardIdentity>>> opened, String podName) {
        this(host, cardsGo, seated, contributors, opened, podName, 0);
    }

    public PodRecord {
        if (host == null || cardsGo == null) {
            throw new IllegalArgumentException("A pod record needs a host and where the cards go");
        }
        if (pickSeconds < 0 || pickSeconds > PodSettings.LONGEST_PICK_SECONDS) {
            throw new IllegalArgumentException("A pick clock is 0 to " + PodSettings.LONGEST_PICK_SECONDS + " seconds");
        }
        seated = List.copyOf(seated);
        List<List<UUID>> whose = new ArrayList<>();
        for (List<UUID> seat : contributors) {
            whose.add(java.util.Collections.unmodifiableList(new ArrayList<>(seat)));
        }
        contributors = List.copyOf(whose);
        List<List<List<CardIdentity>>> held = new ArrayList<>();
        for (List<List<CardIdentity>> seat : opened) {
            List<List<CardIdentity>> packs = new ArrayList<>();
            for (List<CardIdentity> pack : seat) {
                packs.add(List.copyOf(pack));
            }
            held.add(List.copyOf(packs));
        }
        opened = List.copyOf(held);
        if (contributors.size() != seated.size() || opened.size() != seated.size()) {
            throw new IllegalArgumentException("A pod record needs a list for every seat");
        }
        for (int seat = 0; seat < seated.size(); seat++) {
            if (contributors.get(seat).size() != opened.get(seat).size()) {
                throw new IllegalArgumentException("Seat " + seat + " has packs nobody put in");
            }
        }
        podName = podName == null ? "" : podName;
    }

    /** Built from a lobby's plan and what each planned pack held. */
    public static PodRecord of(
            PodLobby lobby, List<UUID> seated, PodLobby.Plan plan,
            List<List<List<CardIdentity>>> opened, String podName) {
        List<List<UUID>> contributors = new ArrayList<>();
        for (List<PodLobby.Entry> packs : plan.bySeat()) {
            List<UUID> whose = new ArrayList<>();
            for (PodLobby.Entry entry : packs) {
                whose.add(entry.contributor());
            }
            contributors.add(whose);
        }
        return new PodRecord(lobby.host(), lobby.settings().cardsGo(), seated, contributors, opened, podName,
                lobby.settings().pickSeconds());
    }

    /** Everything a seat opened, in order: what a sealed player builds from. */
    public List<List<CardIdentity>> sealedPools() {
        List<List<CardIdentity>> pools = new ArrayList<>();
        for (List<List<CardIdentity>> seat : opened) {
            List<CardIdentity> pool = new ArrayList<>();
            seat.forEach(pool::addAll);
            pools.add(List.copyOf(pool));
        }
        return List.copyOf(pools);
    }

    /**
     * Per seat, per pack, the pack as it goes round the ring: {@code rounds[r][s]} is the pack
     * the player in seat {@code s} opens in round {@code r}.
     */
    public List<List<DraftPack>> rounds() {
        int rounds = opened.isEmpty() ? 0 : opened.get(0).size();
        List<List<DraftPack>> byRound = new ArrayList<>();
        for (int round = 0; round < rounds; round++) {
            List<DraftPack> packs = new ArrayList<>();
            for (List<List<CardIdentity>> seat : opened) {
                packs.add(DraftPack.of(seat.get(round)));
            }
            byRound.add(List.copyOf(packs));
        }
        return List.copyOf(byRound);
    }

    /** Who is owed what when the event ends with these pools. See {@link PodShares}. */
    public Map<UUID, List<CardIdentity>> owed(List<List<CardIdentity>> pools) {
        return PodShares.owed(host, cardsGo, seated, contributors, opened, pools);
    }

    /**
     * Who is owed what when the event cannot finish: every contributor gets back what their
     * own packs held, whatever the host chose, because nobody finished drafting anything to
     * keep. Packs the server made go to nobody.
     */
    public Map<UUID, List<CardIdentity>> backToContributors() {
        return PodShares.owed(host, PodSettings.CardsGo.TO_CONTRIBUTORS, seated, contributors, opened,
                sealedPools(), true);
    }

    // ------------------------------------------------------------------ bytes

    /** Two, for the pick clock; one is still read, with none. */
    private static final int VERSION = 2;

    public static byte[] write(PodRecord record) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            uuid(out, record.host());
            out.writeUTF(record.cardsGo().name());
            out.writeUTF(record.podName());
            out.writeInt(record.pickSeconds());
            out.writeInt(record.seated().size());
            for (int seat = 0; seat < record.seated().size(); seat++) {
                uuid(out, record.seated().get(seat));
                List<UUID> whose = record.contributors().get(seat);
                out.writeInt(whose.size());
                for (int pack = 0; pack < whose.size(); pack++) {
                    out.writeBoolean(whose.get(pack) != null);
                    if (whose.get(pack) != null) {
                        uuid(out, whose.get(pack));
                    }
                    DraftBytes.identities(out, record.opened().get(seat).get(pack));
                }
            }
        } catch (IOException impossible) {
            throw new IllegalStateException("Writing to memory failed", impossible);
        }
        return bytes.toByteArray();
    }

    public static PodRecord read(byte[] written) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(written))) {
            int version = in.readInt();
            if (version != 1 && version != VERSION) {
                throw new IOException("A pod record is version " + version + ", this reads 1 and " + VERSION);
            }
            UUID host = uuid(in);
            PodSettings.CardsGo cardsGo = PodSettings.CardsGo.valueOf(in.readUTF());
            String podName = in.readUTF();
            int pickSeconds = version >= 2 ? in.readInt() : 0;
            int seats = DraftBytes.place(in.readInt());
            List<UUID> seated = new ArrayList<>();
            List<List<UUID>> contributors = new ArrayList<>();
            List<List<List<CardIdentity>>> opened = new ArrayList<>();
            for (int seat = 0; seat < seats; seat++) {
                seated.add(uuid(in));
                int packs = DraftBytes.size(in.readInt());
                List<UUID> whose = new ArrayList<>();
                List<List<CardIdentity>> held = new ArrayList<>();
                for (int pack = 0; pack < packs; pack++) {
                    whose.add(in.readBoolean() ? uuid(in) : null);
                    held.add(DraftBytes.identities(in));
                }
                contributors.add(whose);
                opened.add(held);
            }
            return new PodRecord(host, cardsGo, seated, contributors, opened, podName, pickSeconds);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("A saved pod record does not add up: " + malformed.getMessage(), malformed);
        }
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID uuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }
}
