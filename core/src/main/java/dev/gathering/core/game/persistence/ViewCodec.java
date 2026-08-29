package dev.gathering.core.game.persistence;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Facing;
import dev.gathering.core.game.event.CardRef;
import dev.gathering.core.game.event.LogArg;
import dev.gathering.core.game.event.LogEntry;
import dev.gathering.core.game.MarkerId;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.TurnMarker;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.game.visibility.GameView;
import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.Viewer;
import dev.gathering.core.game.visibility.ZoneView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A player's view of the board, to bytes and back.
 *
 * <p>This is what crosses the network to a client, and it matters that it is exactly a
 * {@link GameView} and never a {@code GameState}. The view has already been through the
 * visibility rules: an opponent's hand is a number, a face-down card is a marker with no path
 * back to a card. Writing the state instead - or "the state, minus the bits they should not
 * see" - is how a client ends up holding a secret it was trusted not to look at.
 *
 * <p>Bytes rather than a network codec so the shape lives in the pure module with the rules
 * it mirrors, and so a round trip can be checked against arbitrary views rather than the ones
 * somebody thought to write down.
 */
public final class ViewCodec {

    public static final int VERSION = 2;

    /** A ceiling on any length read from the wire, checked before it sizes anything. */
    public static final int MAX_ENTRIES = 20_000;

    private ViewCodec() {
    }

    public static byte[] write(GameView view) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            viewer(out, view.viewer());
            out.writeBoolean(view.ended());
            turn(out, view.turn());

            out.writeInt(view.seats().size());
            for (SeatView seat : view.seats()) {
                seat(out, seat);
            }

            out.writeInt(view.log().size());
            for (LogEntry entry : view.log()) {
                logEntry(out, entry);
            }
        }
        return bytes.toByteArray();
    }

    public static GameView read(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Board view is version " + version + ", this reads " + VERSION);
            }
            Viewer viewer = viewer(in);
            boolean ended = in.readBoolean();
            TurnMarker turn = turn(in);

            int seatCount = size(in.readInt());
            List<SeatView> seats = new ArrayList<>(seatCount);
            for (int index = 0; index < seatCount; index++) {
                seats.add(seat(in));
            }
            int logCount = size(in.readInt());
            List<LogEntry> log = new ArrayList<>(logCount);
            for (int index = 0; index < logCount; index++) {
                log.add(logEntry(in));
            }
            return new GameView(viewer, seats, turn, ended, log);
        }
    }

    /**
     * One log line.
     *
     * <p>Arguments are tagged by name rather than by ordinal, like every other tagged thing
     * written here: an ordinal means something different the moment the sealed set gains a
     * member, and this crosses versions.
     */
    private static void logEntry(DataOutput out, LogEntry entry) throws IOException {
        out.writeLong(entry.sequence());
        out.writeUTF(entry.key());
        out.writeBoolean(entry.undone());
        out.writeInt(entry.args().size());
        for (LogArg arg : entry.args()) {
            switch (arg) {
                case LogArg.Seat seat -> {
                    out.writeUTF("seat");
                    out.writeInt(seat.seat().index());
                }
                case LogArg.Amount amount -> {
                    out.writeUTF("amount");
                    out.writeInt(amount.value());
                }
                case LogArg.Where where -> {
                    out.writeUTF("zone");
                    out.writeUTF(where.zone().name());
                }
                case LogArg.Text text -> {
                    out.writeUTF("text");
                    out.writeUTF(text.text());
                }
                case LogArg.Card card -> {
                    switch (card.card()) {
                        case CardRef.ById byId -> {
                            out.writeUTF("card");
                            out.writeInt(byId.id().value());
                        }
                        case CardRef.ByMarker byMarker -> {
                            out.writeUTF("marker");
                            out.writeUTF(byMarker.marker().value());
                        }
                        case CardRef.Anonymous ignored -> out.writeUTF("anonymous");
                    }
                }
            }
        }
    }

    private static LogEntry logEntry(DataInput in) throws IOException {
        long sequence = in.readLong();
        String key = in.readUTF();
        boolean undone = in.readBoolean();
        int count = size(in.readInt());
        List<LogArg> args = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String tag = in.readUTF();
            args.add(switch (tag) {
                case "seat" -> new LogArg.Seat(new SeatId(in.readInt()));
                case "amount" -> new LogArg.Amount(in.readInt());
                case "zone" -> new LogArg.Where(Zone.valueOf(in.readUTF()));
                case "text" -> new LogArg.Text(in.readUTF());
                case "card" -> new LogArg.Card(new CardRef.ById(new CardInstanceId(in.readInt())));
                case "marker" -> new LogArg.Card(new CardRef.ByMarker(new MarkerId(in.readUTF())));
                case "anonymous" -> new LogArg.Card(CardRef.ANONYMOUS);
                default -> throw new IOException("Unknown log argument: " + tag);
            });
        }
        return new LogEntry(sequence, key, args, undone);
    }

    private static void viewer(DataOutput out, Viewer viewer) throws IOException {
        switch (viewer) {
            case Viewer.Seated seated -> {
                out.writeBoolean(true);
                out.writeInt(seated.seat().index());
            }
            case Viewer.Spectator ignored -> out.writeBoolean(false);
        }
    }

    private static Viewer viewer(DataInput in) throws IOException {
        return in.readBoolean() ? new Viewer.Seated(new SeatId(in.readInt())) : new Viewer.Spectator();
    }

    private static void turn(DataOutput out, TurnMarker turn) throws IOException {
        boolean present = turn != null;
        out.writeBoolean(present);
        if (present) {
            out.writeInt(turn.activeSeat().index());
            out.writeInt(turn.turnNumber());
        }
    }

    private static TurnMarker turn(DataInput in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        return new TurnMarker(new SeatId(in.readInt()), in.readInt());
    }

    private static void seat(DataOutput out, SeatView seat) throws IOException {
        out.writeInt(seat.seat().index());

        writePlayer(out, seat.player());
        // And whoever last held the chair, which outlives them leaving it. A board outlasts
        // its player, and a board nobody's name is on is one nobody can write a sentence
        // about: the log, the life total and the title over a pile all want this.
        writePlayer(out, seat.lastPlayer());

        out.writeInt(seat.life());
        out.writeBoolean(seat.conceded());

        out.writeInt(seat.commanderDamage().size());
        for (Map.Entry<CardInstanceId, Integer> entry : seat.commanderDamage().entrySet()) {
            out.writeInt(entry.getKey().value());
            out.writeInt(entry.getValue());
        }

        out.writeInt(seat.commanders().size());
        for (CardInstanceId commander : seat.commanders()) {
            out.writeInt(commander.value());
        }

        out.writeInt(seat.commanderTax().size());
        for (Map.Entry<CardInstanceId, Integer> entry : seat.commanderTax().entrySet()) {
            out.writeInt(entry.getKey().value());
            out.writeInt(entry.getValue());
        }

        out.writeInt(seat.counters().size());
        for (Map.Entry<String, Integer> entry : seat.counters().entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeInt(entry.getValue());
        }

        // Who this hand is turned towards. Public, and the reason it has to be on the wire
        // rather than worked out: the player showing it needs their own screen to keep saying
        // so, and the players being shown it need to know why they can suddenly read it.
        out.writeInt(seat.handShownTo().size());
        for (SeatId shown : seat.handShownTo()) {
            out.writeInt(shown.index());
        }

        out.writeInt(seat.zones().size());
        for (Map.Entry<Zone, ZoneView> entry : seat.zones().entrySet()) {
            out.writeUTF(entry.getKey().name());
            zone(out, entry.getValue());
        }
    }

    private static SeatView seat(DataInput in) throws IOException {
        SeatId id = new SeatId(in.readInt());
        PlayerRef player = readPlayer(in);
        // Whoever last held the chair, which outlives them leaving it. Written separately
        // rather than inferred, because a viewer has no other way to know whose board they
        // are looking at once the chair is free.
        PlayerRef lastPlayer = readPlayer(in);
        int life = in.readInt();
        boolean conceded = in.readBoolean();

        Map<CardInstanceId, Integer> damage = new LinkedHashMap<>();
        int damageCount = size(in.readInt());
        for (int index = 0; index < damageCount; index++) {
            damage.put(new CardInstanceId(in.readInt()), in.readInt());
        }

        List<CardInstanceId> commanders = new ArrayList<>();
        int commanderCount = size(in.readInt());
        for (int index = 0; index < commanderCount; index++) {
            commanders.add(new CardInstanceId(in.readInt()));
        }

        Map<CardInstanceId, Integer> tax = new LinkedHashMap<>();
        int taxCount = size(in.readInt());
        for (int index = 0; index < taxCount; index++) {
            tax.put(new CardInstanceId(in.readInt()), in.readInt());
        }

        Map<String, Integer> counters = new LinkedHashMap<>();
        int counterCount = size(in.readInt());
        for (int index = 0; index < counterCount; index++) {
            counters.put(in.readUTF(), in.readInt());
        }

        java.util.Set<SeatId> handShownTo = new java.util.LinkedHashSet<>();
        int shownCount = size(in.readInt());
        for (int index = 0; index < shownCount; index++) {
            handShownTo.add(new SeatId(in.readInt()));
        }

        Map<Zone, ZoneView> zones = new EnumMap<>(Zone.class);
        int zoneCount = size(in.readInt());
        for (int index = 0; index < zoneCount; index++) {
            Zone zone = Zone.valueOf(in.readUTF());
            zones.put(zone, zone(in));
        }
        return new SeatView(
                id, player, lastPlayer, life, damage, tax, commanders, counters, conceded,
                handShownTo, zones);
    }

    private static PlayerRef readPlayer(DataInput in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        UUID uuid = new UUID(in.readLong(), in.readLong());
        return new PlayerRef(uuid, in.readUTF());
    }

    private static void writePlayer(DataOutput out, PlayerRef player) throws IOException {
        out.writeBoolean(player != null);
        if (player != null) {
            out.writeLong(player.id().getMostSignificantBits());
            out.writeLong(player.id().getLeastSignificantBits());
            out.writeUTF(player.name());
        }
    }

    private static void zone(DataOutput out, ZoneView zone) throws IOException {
        out.writeInt(zone.ref().seat().index());
        out.writeUTF(zone.ref().zone().name());
        out.writeInt(zone.count());

        // A count-only zone carries no cards at all - not an empty list of redacted ones.
        // Writing the list unconditionally would make the two indistinguishable here and
        // invite somebody to "fix" it by sending redacted entries.
        out.writeBoolean(zone.isCountOnly());
        if (!zone.isCountOnly()) {
            out.writeInt(zone.cards().size());
            for (CardView card : zone.cards()) {
                card(out, card);
            }
        }
    }

    private static ZoneView zone(DataInput in) throws IOException {
        ZoneRef ref = new ZoneRef(new SeatId(in.readInt()), Zone.valueOf(in.readUTF()));
        int count = size(in.readInt());
        if (in.readBoolean()) {
            return ZoneView.countOnly(ref, count);
        }
        int cardCount = size(in.readInt());
        List<CardView> cards = new ArrayList<>(cardCount);
        for (int index = 0; index < cardCount; index++) {
            cards.add(card(in));
        }
        return new ZoneView(ref, count, cards);
    }

    private static void card(DataOutput out, CardView card) throws IOException {
        switch (card) {
            case CardView.Visible visible -> {
                out.writeBoolean(true);
                out.writeInt(visible.id().value());
                identity(out, visible.identity());
                out.writeInt(visible.owner().index());
                out.writeUTF(visible.facing().name());
                out.writeBoolean(visible.tapped());
                counters(out, visible.counters());
                position(out, visible.position());
                out.writeBoolean(visible.token());
                host(out, visible.attachedTo());
                out.writeUTF(visible.note() == null ? "" : visible.note());
                out.writeBoolean(visible.turnedOver());
                out.writeUTF(visible.strength() == null ? "" : visible.strength());
                out.writeBoolean(visible.frozen());

            }
            case CardView.Anonymous anonymous -> {
                out.writeBoolean(false);
                out.writeUTF(anonymous.marker().value());
                out.writeBoolean(anonymous.tapped());
                counters(out, anonymous.counters());
                position(out, anonymous.position());
                host(out, anonymous.attachedTo());
                out.writeUTF(anonymous.note() == null ? "" : anonymous.note());
                out.writeUTF(anonymous.strength() == null ? "" : anonymous.strength());
                out.writeBoolean(anonymous.frozen());
            }
        }
    }

    private static CardView card(DataInput in) throws IOException {
        if (in.readBoolean()) {
            return new CardView.Visible(
                    new CardInstanceId(in.readInt()),
                    identity(in),
                    new SeatId(in.readInt()),
                    Facing.valueOf(in.readUTF()),
                    in.readBoolean(),
                    counters(in),
                    position(in),
                    in.readBoolean(),
                    host(in),
                    in.readUTF(),
                    in.readBoolean(),
                    in.readUTF(),
                    in.readBoolean());
        }
        return new CardView.Anonymous(
                new MarkerId(in.readUTF()), in.readBoolean(), counters(in), position(in), host(in),
                in.readUTF(),
                in.readUTF(),
                in.readBoolean());
    }

    private static void host(DataOutput out, CardInstanceId host) throws IOException {
        out.writeBoolean(host != null);
        if (host != null) {
            out.writeInt(host.value());
        }
    }

    private static CardInstanceId host(DataInput in) throws IOException {
        return in.readBoolean() ? new CardInstanceId(in.readInt()) : null;
    }

    // A card's identity has one wire shape. It was written out twice, byte for byte the
    // same, here and in EventCodec - two codecs that agree by coincidence are a saved
    // session and a client view one edit away from silently disagreeing.
    private static void identity(DataOutput out, CardIdentity identity) throws IOException {
        EventCodec.identity(out, identity);
    }

    private static CardIdentity identity(DataInput in) throws IOException {
        return EventCodec.identity(in);
    }

    private static void counters(DataOutput out, Map<String, Integer> counters) throws IOException {
        out.writeInt(counters.size());
        for (Map.Entry<String, Integer> entry : counters.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeInt(entry.getValue());
        }
    }

    private static Map<String, Integer> counters(DataInput in) throws IOException {
        int count = size(in.readInt());
        Map<String, Integer> counters = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            counters.put(in.readUTF(), in.readInt());
        }
        return counters;
    }

    private static void position(DataOutput out, TablePosition position) throws IOException {
        boolean present = position != null;
        out.writeBoolean(present);
        if (present) {
            out.writeInt(position.x());
            out.writeInt(position.y());
            out.writeInt(position.rotation());
        }
    }

    private static TablePosition position(DataInput in) throws IOException {
        return in.readBoolean()
                ? new TablePosition(in.readInt(), in.readInt(), in.readInt())
                : null;
    }

    private static int size(int size) throws IOException {
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IOException("Implausible length in a board view: " + size);
        }
        return size;
    }
}
