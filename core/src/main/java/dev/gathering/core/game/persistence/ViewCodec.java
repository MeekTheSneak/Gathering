package dev.gathering.core.game.persistence;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.MarkerId;
import dev.gathering.core.game.Phase;
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

    public static final int VERSION = 1;

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
            return new GameView(viewer, seats, turn, ended);
        }
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
            out.writeUTF(turn.phase().name());
            out.writeInt(turn.turnNumber());
        }
    }

    private static TurnMarker turn(DataInput in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        return new TurnMarker(new SeatId(in.readInt()), Phase.valueOf(in.readUTF()), in.readInt());
    }

    private static void seat(DataOutput out, SeatView seat) throws IOException {
        out.writeInt(seat.seat().index());

        boolean occupied = seat.player() != null;
        out.writeBoolean(occupied);
        if (occupied) {
            out.writeLong(seat.player().id().getMostSignificantBits());
            out.writeLong(seat.player().id().getLeastSignificantBits());
            out.writeUTF(seat.player().name());
        }

        out.writeInt(seat.life());
        out.writeBoolean(seat.conceded());

        out.writeInt(seat.commanderDamage().size());
        for (Map.Entry<SeatId, Integer> entry : seat.commanderDamage().entrySet()) {
            out.writeInt(entry.getKey().index());
            out.writeInt(entry.getValue());
        }

        out.writeInt(seat.commanderTax().size());
        for (Map.Entry<CardInstanceId, Integer> entry : seat.commanderTax().entrySet()) {
            out.writeInt(entry.getKey().value());
            out.writeInt(entry.getValue());
        }

        out.writeInt(seat.zones().size());
        for (Map.Entry<Zone, ZoneView> entry : seat.zones().entrySet()) {
            out.writeUTF(entry.getKey().name());
            zone(out, entry.getValue());
        }
    }

    private static SeatView seat(DataInput in) throws IOException {
        SeatId id = new SeatId(in.readInt());
        PlayerRef player = null;
        if (in.readBoolean()) {
            UUID uuid = new UUID(in.readLong(), in.readLong());
            player = new PlayerRef(uuid, in.readUTF());
        }
        int life = in.readInt();
        boolean conceded = in.readBoolean();

        Map<SeatId, Integer> damage = new LinkedHashMap<>();
        int damageCount = size(in.readInt());
        for (int index = 0; index < damageCount; index++) {
            damage.put(new SeatId(in.readInt()), in.readInt());
        }

        Map<CardInstanceId, Integer> tax = new LinkedHashMap<>();
        int taxCount = size(in.readInt());
        for (int index = 0; index < taxCount; index++) {
            tax.put(new CardInstanceId(in.readInt()), in.readInt());
        }

        Map<Zone, ZoneView> zones = new EnumMap<>(Zone.class);
        int zoneCount = size(in.readInt());
        for (int index = 0; index < zoneCount; index++) {
            Zone zone = Zone.valueOf(in.readUTF());
            zones.put(zone, zone(in));
        }
        return new SeatView(id, player, life, damage, tax, conceded, zones);
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
                out.writeBoolean(visible.tapped());
                counters(out, visible.counters());
                position(out, visible.position());
                out.writeBoolean(visible.token());
            }
            case CardView.Anonymous anonymous -> {
                out.writeBoolean(false);
                out.writeUTF(anonymous.marker().value());
                out.writeBoolean(anonymous.tapped());
                counters(out, anonymous.counters());
                position(out, anonymous.position());
            }
        }
    }

    private static CardView card(DataInput in) throws IOException {
        if (in.readBoolean()) {
            return new CardView.Visible(
                    new CardInstanceId(in.readInt()),
                    identity(in),
                    new SeatId(in.readInt()),
                    in.readBoolean(),
                    counters(in),
                    position(in),
                    in.readBoolean());
        }
        return new CardView.Anonymous(
                new MarkerId(in.readUTF()), in.readBoolean(), counters(in), position(in));
    }

    private static void identity(DataOutput out, CardIdentity identity) throws IOException {
        boolean printing = identity.scryfallId() != null;
        out.writeBoolean(printing);
        if (printing) {
            out.writeLong(identity.scryfallId().getMostSignificantBits());
            out.writeLong(identity.scryfallId().getLeastSignificantBits());
        } else {
            out.writeUTF(identity.customId());
        }
        out.writeBoolean(identity.foil());
    }

    private static CardIdentity identity(DataInput in) throws IOException {
        if (in.readBoolean()) {
            UUID id = new UUID(in.readLong(), in.readLong());
            return CardIdentity.ofPrinting(id, in.readBoolean());
        }
        return CardIdentity.ofCustom(in.readUTF(), in.readBoolean());
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
            out.writeInt(position.column());
            out.writeInt(position.row());
        }
    }

    private static TablePosition position(DataInput in) throws IOException {
        return in.readBoolean() ? new TablePosition(in.readInt(), in.readInt()) : null;
    }

    private static int size(int size) throws IOException {
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IOException("Implausible length in a board view: " + size);
        }
        return size;
    }
}
