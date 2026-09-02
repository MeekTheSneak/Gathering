package dev.gathering.core.game.persistence;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.PaperStock;
import dev.gathering.core.game.CardInstanceId;
import dev.gathering.core.game.Facing;
import dev.gathering.core.game.Placement;
import dev.gathering.core.game.PlayerRef;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.game.ZoneRef;
import dev.gathering.core.game.event.GameEvent;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One game event, to bytes and back.
 * <p>A session is its event log - state is the fold of it - so this is what makes a game
 * survive a restart. Bytes rather than any of Minecraft's own serialization because it lives
 * in the pure module, which is what lets a round trip be checked against arbitrary logs
 * instead of against the handful somebody thought to write down. It is also what the sealed
 * stream needs: encryption takes bytes.
 * <p>Events are tagged by <b>name</b>, never by ordinal or declaration order. An ordinal
 * means something different the moment the list gains an entry, and this is a save file. An
 * unknown name is a hard failure rather than a skipped event: a log with a hole in it folds
 * to a board that never existed.
 */
public final class EventCodec {

    private EventCodec() {
    }

    public static void write(DataOutput out, GameEvent event) throws IOException {
        String tag = event.getClass().getSimpleName();
        out.writeUTF(tag);
        switch (event) {
            case GameEvent.SeatTaken e -> {
                seat(out, e.actor());
                player(out, e.player());
            }
            case GameEvent.SeatReleased e -> seat(out, e.actor());
            case GameEvent.DeckLoaded e -> {
                seat(out, e.actor());
                identities(out, e.library());
                identities(out, e.commanders());
                out.writeUTF(e.sleeve().name());
            }
            case GameEvent.SessionEnded e -> {
                seat(out, e.actor());
                out.writeUTF(e.reason());
            }
            case GameEvent.CardMoved e -> {
                seat(out, e.actor());
                card(out, e.card());
                zone(out, e.to());
                placement(out, e.placement());
            }
            case GameEvent.ZoneMoved e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                out.writeUTF(e.from().name());
                zone(out, e.to());
                placement(out, e.placement());
            }
            case GameEvent.CardTurnedOver e -> {
                seat(out, e.actor());
                card(out, e.card());
                out.writeBoolean(e.showingTheOtherSide());
            }
            case GameEvent.CardNoted e -> {
                seat(out, e.actor());
                card(out, e.card());
                out.writeUTF(e.note() == null ? "" : e.note());
            }
            case GameEvent.HandSorted e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                cards(out, e.order());
            }
            case GameEvent.CardFrozen e -> {
                seat(out, e.actor());
                card(out, e.card());
                out.writeBoolean(e.frozen());
            }
            case GameEvent.CardStrengthSet e -> {
                seat(out, e.actor());
                card(out, e.card());
                out.writeUTF(e.strength() == null ? "" : e.strength());
            }
            case GameEvent.CardTapSet e -> {
                seat(out, e.actor());
                card(out, e.card());
                out.writeBoolean(e.tapped());
            }
            case GameEvent.CardAttached e -> {
                seat(out, e.actor());
                card(out, e.card());
                out.writeBoolean(e.host() != null);
                if (e.host() != null) {
                    card(out, e.host());
                }
            }
            case GameEvent.CardRotated e -> {
                seat(out, e.actor());
                card(out, e.card());
                out.writeInt(e.rotation());
            }
            case GameEvent.SeatUntappedAll e -> {
                seat(out, e.actor());
                seat(out, e.seat());
            }
            case GameEvent.CardFacingSet e -> {
                seat(out, e.actor());
                card(out, e.card());
                out.writeUTF(e.facing().name());
            }
            case GameEvent.LibraryClosed e -> seat(out, e.actor());
            case GameEvent.LibraryMilled e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                out.writeInt(e.count());
            }
            case GameEvent.LibraryExiled e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                out.writeInt(e.count());
            }
            case GameEvent.LibraryRevealed e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                out.writeInt(e.count());
            }
            case GameEvent.CardsDrawn e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                out.writeInt(e.count());
            }
            case GameEvent.Mulliganed e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                out.writeInt(e.newHandSize());
            }
            case GameEvent.LibraryShuffled e -> {
                seat(out, e.actor());
                seat(out, e.seat());
            }
            case GameEvent.LibrarySearched e -> {
                seat(out, e.actor());
                seat(out, e.seat());
            }
            case GameEvent.LibraryLooked e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                out.writeInt(e.count());
            }
            case GameEvent.LibraryReordered e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                cards(out, e.onTop());
                cards(out, e.toBottom());
            }
            case GameEvent.Surveiled e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                cards(out, e.onTop());
                cards(out, e.toGraveyard());
            }
            case GameEvent.CounterChanged e -> {
                seat(out, e.actor());
                card(out, e.card());
                out.writeUTF(e.counter());
                out.writeInt(e.delta());
            }
            case GameEvent.TokenCreated e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                identity(out, e.identity());
                out.writeInt(e.count());
            }
            case GameEvent.HandShown e -> {
                seat(out, e.actor());
                // A flag then the seat, the same shape an optional card takes above: null
                // means the whole table, and there is no seat index that could stand for it.
                out.writeBoolean(e.to() != null);
                if (e.to() != null) {
                    seat(out, e.to());
                }
                out.writeBoolean(e.showing());
            }
            case GameEvent.PaperCardCreated e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                out.writeUTF(e.stock().name());
                out.writeUTF(e.text() == null ? "" : e.text());
            }
            case GameEvent.TokenCopyCreated e -> {
                seat(out, e.actor());
                card(out, e.source());
                seat(out, e.seat());
            }
            case GameEvent.TokenRemoved e -> {
                seat(out, e.actor());
                card(out, e.card());
            }
            case GameEvent.SeatCounterChanged e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                out.writeUTF(e.counter());
                out.writeInt(e.delta());
            }
            case GameEvent.LifeChanged e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                out.writeInt(e.delta());
            }
            case GameEvent.CommanderDamageChanged e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                card(out, e.commander());
                out.writeInt(e.delta());
            }
            case GameEvent.CommanderTaxChanged e -> {
                seat(out, e.actor());
                seat(out, e.seat());
                card(out, e.commander());
                out.writeInt(e.delta());
            }
            case GameEvent.Conceded e -> seat(out, e.actor());
            case GameEvent.TurnPassed e -> {
                seat(out, e.actor());
                seat(out, e.toSeat());
            }
            case GameEvent.DiceRolled e -> {
                seat(out, e.actor());
                out.writeInt(e.sides());
                out.writeInt(e.result());
            }
            case GameEvent.CoinFlipped e -> {
                seat(out, e.actor());
                out.writeBoolean(e.heads());
            }
            case GameEvent.PlanarRolled e -> {
                seat(out, e.actor());
                out.writeUTF(e.face().name());
            }
            case GameEvent.CardPinged e -> {
                seat(out, e.actor());
                card(out, e.card());
            }
        }
    }

    /** A deck arriving, from either shape of log. */
    private static GameEvent deckLoaded(DataInput in, boolean sleeved) throws IOException {
        SeatId actor = seat(in);
        java.util.List<dev.gathering.core.card.CardIdentity> library = identities(in);
        java.util.List<dev.gathering.core.card.CardIdentity> commanders = identities(in);
        return new GameEvent.DeckLoaded(actor, library, commanders,
                sleeved ? dev.gathering.core.card.Sleeve.named(in.readUTF())
                        : dev.gathering.core.card.Sleeve.DEFAULT);
    }

    /**
     * Reads an event written by the current build.
     * <p>What comes off a socket is always current, because both ends are this build. Only a
     * log read back off disk can be older, and that one says how old it is.
     */
    public static GameEvent read(DataInput in) throws IOException {
        return read(in, true);
    }

    /**
     * Reads an event, told whether the stream is new enough to carry a deck's sleeve.
     *
     * @param sleeved false for a log written before decks could be sleeved, whose DeckLoaded
     *     ends after the commanders. Passing true for one of those reads the next record's
     *     tag as a sleeve name and everything after it is nonsense - which is why the caller
     *     is made to say rather than being allowed to guess.
     */
    public static GameEvent read(DataInput in, boolean sleeved) throws IOException {
        String tag = in.readUTF();
        return switch (tag) {
            case "SeatTaken" -> new GameEvent.SeatTaken(seat(in), player(in));
            case "SeatReleased" -> new GameEvent.SeatReleased(seat(in));
            case "DeckLoaded" -> deckLoaded(in, sleeved);
            case "SessionEnded" -> new GameEvent.SessionEnded(seat(in), in.readUTF());
            case "CardMoved" -> new GameEvent.CardMoved(seat(in), card(in), zone(in), placement(in));
            case "ZoneMoved" -> new GameEvent.ZoneMoved(
                    seat(in), seat(in), Zone.valueOf(in.readUTF()), zone(in), placement(in));
            case "CardTurnedOver" -> new GameEvent.CardTurnedOver(
                    seat(in), card(in), in.readBoolean());
            case "CardNoted" -> new GameEvent.CardNoted(seat(in), card(in), in.readUTF());
            case "HandSorted" -> new GameEvent.HandSorted(seat(in), seat(in), cards(in));
            case "CardFrozen" -> new GameEvent.CardFrozen(seat(in), card(in), in.readBoolean());
            case "CardStrengthSet" -> new GameEvent.CardStrengthSet(seat(in), card(in), in.readUTF());
            case "CardTapSet" -> new GameEvent.CardTapSet(seat(in), card(in), in.readBoolean());
            case "CardAttached" -> new GameEvent.CardAttached(
                    seat(in), card(in), in.readBoolean() ? card(in) : null);
            case "CardRotated" -> new GameEvent.CardRotated(seat(in), card(in), in.readInt());
            case "SeatUntappedAll" -> new GameEvent.SeatUntappedAll(seat(in), seat(in));
            case "CardFacingSet" -> new GameEvent.CardFacingSet(
                    seat(in), card(in), Facing.valueOf(in.readUTF()));
            case "LibraryClosed" -> new GameEvent.LibraryClosed(seat(in));
            case "LibraryMilled" -> new GameEvent.LibraryMilled(seat(in), seat(in), in.readInt());
            case "LibraryExiled" -> new GameEvent.LibraryExiled(seat(in), seat(in), in.readInt());
            case "LibraryRevealed" -> new GameEvent.LibraryRevealed(seat(in), seat(in), in.readInt());
            case "CardsDrawn" -> new GameEvent.CardsDrawn(seat(in), seat(in), in.readInt());
            case "Mulliganed" -> new GameEvent.Mulliganed(seat(in), seat(in), in.readInt());
            case "LibraryShuffled" -> new GameEvent.LibraryShuffled(seat(in), seat(in));
            case "LibrarySearched" -> new GameEvent.LibrarySearched(seat(in), seat(in));
            case "LibraryLooked" -> new GameEvent.LibraryLooked(seat(in), seat(in), in.readInt());
            case "LibraryReordered" -> new GameEvent.LibraryReordered(
                    seat(in), seat(in), cards(in), cards(in));
            case "Surveiled" -> new GameEvent.Surveiled(seat(in), seat(in), cards(in), cards(in));
            case "CounterChanged" -> new GameEvent.CounterChanged(
                    seat(in), card(in), in.readUTF(), in.readInt());
            case "TokenCreated" -> new GameEvent.TokenCreated(
                    seat(in), seat(in), identity(in), in.readInt());
            case "HandShown" -> new GameEvent.HandShown(
                    seat(in), in.readBoolean() ? seat(in) : null, in.readBoolean());
            case "PaperCardCreated" ->  new GameEvent.PaperCardCreated(
                    seat(in), seat(in), PaperStock.valueOf(in.readUTF()), in.readUTF());
            case "TokenCopyCreated" -> new GameEvent.TokenCopyCreated(seat(in), card(in), seat(in));
            case "TokenRemoved" -> new GameEvent.TokenRemoved(seat(in), card(in));
            case "SeatCounterChanged" -> new GameEvent.SeatCounterChanged(
                    seat(in), seat(in), in.readUTF(), in.readInt());
            case "LifeChanged" -> new GameEvent.LifeChanged(seat(in), seat(in), in.readInt());
            case "CommanderDamageChanged" -> new GameEvent.CommanderDamageChanged(
                    seat(in), seat(in), card(in), in.readInt());
            case "CommanderTaxChanged" -> new GameEvent.CommanderTaxChanged(
                    seat(in), seat(in), card(in), in.readInt());
            case "Conceded" -> new GameEvent.Conceded(seat(in));
            // A retired verb. The phase marker is gone - nothing ever read it and no table
            // ever moved it - but a session written while it existed still has these in its
            // log, and refusing to open somebody's half-finished game over a label is the
            // wrong trade. Read past it and hand back nothing; the caller drops the record.
            case "PhaseSet" -> {
                seat(in);
                in.readUTF();
                yield null;
            }
            case "TurnPassed" -> new GameEvent.TurnPassed(seat(in), seat(in));
            case "DiceRolled" -> new GameEvent.DiceRolled(seat(in), in.readInt(), in.readInt());
            case "CoinFlipped" -> new GameEvent.CoinFlipped(seat(in), in.readBoolean());
            case "PlanarRolled" -> new GameEvent.PlanarRolled(
                    seat(in), dev.gathering.core.game.event.PlanarFace.valueOf(in.readUTF()));
            case "CardPinged" -> new GameEvent.CardPinged(seat(in), card(in));
            default -> throw new IOException("Unknown event in the session log: " + tag);
        };
    }

    // ------------------------------------------------------------ value types

    private static void seat(DataOutput out, SeatId seat) throws IOException {
        out.writeInt(seat.index());
    }

    private static SeatId seat(DataInput in) throws IOException {
        return new SeatId(in.readInt());
    }

    private static void card(DataOutput out, CardInstanceId card) throws IOException {
        out.writeInt(card.value());
    }

    private static CardInstanceId card(DataInput in) throws IOException {
        return new CardInstanceId(in.readInt());
    }

    private static void player(DataOutput out, PlayerRef player) throws IOException {
        out.writeLong(player.id().getMostSignificantBits());
        out.writeLong(player.id().getLeastSignificantBits());
        out.writeUTF(player.name());
    }

    private static PlayerRef player(DataInput in) throws IOException {
        UUID id = new UUID(in.readLong(), in.readLong());
        return new PlayerRef(id, in.readUTF());
    }

    private static void zone(DataOutput out, ZoneRef zone) throws IOException {
        seat(out, zone.seat());
        out.writeUTF(zone.zone().name());
    }

    private static ZoneRef zone(DataInput in) throws IOException {
        return new ZoneRef(seat(in), Zone.valueOf(in.readUTF()));
    }

    private static void placement(DataOutput out, Placement placement) throws IOException {
        switch (placement) {
            case Placement.Top ignored -> out.writeUTF("Top");
            case Placement.Bottom ignored -> out.writeUTF("Bottom");
            case Placement.At at -> {
                out.writeUTF("At");
                out.writeInt(at.position().x());
                out.writeInt(at.position().y());
                out.writeInt(at.position().rotation());
            }
        }
    }

    private static Placement placement(DataInput in) throws IOException {
        String tag = in.readUTF();
        return switch (tag) {
            case "Top" -> new Placement.Top();
            case "Bottom" -> new Placement.Bottom();
            case "At" -> new Placement.At(
                    new TablePosition(in.readInt(), in.readInt(), in.readInt()));
            default -> throw new IOException("Unknown placement in the session log: " + tag);
        };
    }

    /** One encoding for a card's identity, shared with {@link ViewCodec} so it cannot drift. */
    static void identity(DataOutput out, CardIdentity identity) throws IOException {
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

    static CardIdentity identity(DataInput in) throws IOException {
        boolean printing = in.readBoolean();
        if (printing) {
            UUID id = new UUID(in.readLong(), in.readLong());
            return CardIdentity.ofPrinting(id, in.readBoolean());
        }
        String custom = in.readUTF();
        return CardIdentity.ofCustom(custom, in.readBoolean());
    }

    private static void identities(DataOutput out, List<CardIdentity> list) throws IOException {
        out.writeInt(list.size());
        for (CardIdentity identity : list) {
            identity(out, identity);
        }
    }

    private static List<CardIdentity> identities(DataInput in) throws IOException {
        int size = readSize(in);
        List<CardIdentity> list = new ArrayList<>(Math.min(size, 1024));
        for (int index = 0; index < size; index++) {
            list.add(identity(in));
        }
        return List.copyOf(list);
    }

    private static void cards(DataOutput out, List<CardInstanceId> list) throws IOException {
        out.writeInt(list.size());
        for (CardInstanceId card : list) {
            card(out, card);
        }
    }

    private static List<CardInstanceId> cards(DataInput in) throws IOException {
        int size = readSize(in);
        List<CardInstanceId> list = new ArrayList<>(Math.min(size, 1024));
        for (int index = 0; index < size; index++) {
            list.add(card(in));
        }
        return List.copyOf(list);
    }

    /**
     * A length from the stream, sanity-checked before it is used to size anything.
     * <p>A corrupt or hostile length would otherwise be an allocation of whatever it says.
     */
    private static int readSize(DataInput in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > SessionCodec.MAX_LIST) {
            throw new IOException("Implausible list length in the session log: " + size);
        }
        return size;
    }
}
