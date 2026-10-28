package dev.gathering.network;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: one tournament as the player it is sent to sees it.
 * <p>Standings, pairings and results are public to everybody in an event. Nothing here carries
 * anybody's rating or seed.
 */
public record EventViewPayload(
        UUID id, String name, String host, boolean youHost, String phase, String kind, String format,
        int bestOf, int roundMinutes, int buildMinutes, int topCut, String decks, boolean largeEvent,
        int round, int plannedRounds, int secondsLeft, boolean timeCalled, boolean elimination,
        boolean registered, boolean checkedIn, boolean ready, boolean dropped, int players,
        List<Row> standings, List<Match> pairings, Mine mine, List<String> prizes, List<String> places,
        List<String> hostRefusals, boolean show)
        implements CustomPacketPayload {

    /**
     * For the host: why each of their controls does not apply now, in {@code HostActions.Action}
     * order, blank for a control that does. Empty for anybody else. Worked out on the server by the
     * rules it refuses those actions with, so the screen can gray a control rather than offer it.
     */
    public java.util.Optional<String> refusalOf(dev.gathering.core.tournament.HostActions.Action action) {
        if (action.ordinal() >= hostRefusals.size()) {
            return java.util.Optional.of("message.gathering.event.host_only");
        }
        String why = hostRefusals.get(action.ordinal());
        return why.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(why);
    }

    /** A line of the standings. Percentages in tenths of a percent. */
    public record Row(int rank, String name, int points, int wins, int losses, int draws, int omw, int gw, int ogw,
            boolean dropped) {

        static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = StreamCodec.of(
                (buffer, row) -> {
                    buffer.writeVarInt(row.rank());
                    buffer.writeUtf(row.name(), 64);
                    buffer.writeVarInt(row.points());
                    buffer.writeVarInt(row.wins());
                    buffer.writeVarInt(row.losses());
                    buffer.writeVarInt(row.draws());
                    buffer.writeVarInt(row.omw());
                    buffer.writeVarInt(row.gw());
                    buffer.writeVarInt(row.ogw());
                    buffer.writeBoolean(row.dropped());
                },
                buffer -> new Row(buffer.readVarInt(), buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readBoolean()));
    }

    /**
     * A match this round.
     *
     * @param status pending, reported, disputed, confirmed or bye
     * @param result the confirmed result from the first player's chair, like "2-1-0", or blank
     */
    public record Match(int table, String a, String b, String status, String result, UUID idA, UUID idB) {

        static final StreamCodec<RegistryFriendlyByteBuf, Match> CODEC = StreamCodec.of(
                (buffer, match) -> {
                    buffer.writeVarInt(match.table());
                    buffer.writeUtf(match.a(), 64);
                    buffer.writeUtf(match.b(), 64);
                    buffer.writeUtf(match.status(), 16);
                    buffer.writeUtf(match.result(), 16);
                    buffer.writeUUID(match.idA());
                    buffer.writeUUID(match.idB());
                },
                buffer -> new Match(buffer.readVarInt(), buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(16),
                        buffer.readUtf(16), buffer.readUUID(), buffer.readUUID()));
    }

    /**
     * The receiving player's own match, if they have one this round.
     *
     * @param table      its table, 0 for a bye, -1 for none
     * @param opponent   who they play
     * @param suggested  what the table saw, as "mine-theirs", or blank
     * @param myReport   what they reported, or blank
     * @param theirReport what the opponent reported, as the receiving player sees it, or blank
     * @param confirmed  the confirmed result as they see it, or blank
     * @param extraTurns extra turns taken at their table since time, or -1
     */
    public record Mine(int table, String opponent, String suggested, String myReport, String theirReport,
            String confirmed, int extraTurns) {

        public static final Mine NONE = new Mine(-1, "", "", "", "", "", -1);

        static final StreamCodec<RegistryFriendlyByteBuf, Mine> CODEC = StreamCodec.of(
                (buffer, mine) -> {
                    buffer.writeVarInt(mine.table() + 1);
                    buffer.writeUtf(mine.opponent(), 64);
                    buffer.writeUtf(mine.suggested(), 16);
                    buffer.writeUtf(mine.myReport(), 16);
                    buffer.writeUtf(mine.theirReport(), 16);
                    buffer.writeUtf(mine.confirmed(), 16);
                    buffer.writeVarInt(mine.extraTurns() + 1);
                },
                buffer -> new Mine(buffer.readVarInt() - 1, buffer.readUtf(64), buffer.readUtf(16), buffer.readUtf(16),
                        buffer.readUtf(16), buffer.readUtf(16), buffer.readVarInt() - 1));
    }

    public static final int MOST_ROWS = 256;

    public static final CustomPacketPayload.Type<EventViewPayload> TYPE = GatheringPayloads.type("event_view");

    public static final StreamCodec<RegistryFriendlyByteBuf, EventViewPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, view) -> {
                buffer.writeUUID(view.id());
                buffer.writeUtf(view.name(), 64);
                buffer.writeUtf(view.host(), 64);
                buffer.writeBoolean(view.youHost());
                buffer.writeUtf(view.phase(), 32);
                buffer.writeUtf(view.kind(), 32);
                buffer.writeUtf(view.format(), 64);
                buffer.writeVarInt(view.bestOf());
                buffer.writeVarInt(view.roundMinutes());
                buffer.writeVarInt(view.buildMinutes());
                buffer.writeVarInt(view.topCut());
                buffer.writeUtf(view.decks(), 16);
                buffer.writeBoolean(view.largeEvent());
                buffer.writeVarInt(view.round());
                buffer.writeVarInt(view.plannedRounds());
                buffer.writeVarInt(view.secondsLeft() + 1);
                buffer.writeBoolean(view.timeCalled());
                buffer.writeBoolean(view.elimination());
                buffer.writeBoolean(view.registered());
                buffer.writeBoolean(view.checkedIn());
                buffer.writeBoolean(view.ready());
                buffer.writeBoolean(view.dropped());
                buffer.writeVarInt(view.players());
                Row.CODEC.apply(ByteBufCodecs.list(MOST_ROWS)).encode(buffer, view.standings());
                Match.CODEC.apply(ByteBufCodecs.list(MOST_ROWS)).encode(buffer, view.pairings());
                Mine.CODEC.encode(buffer, view.mine());
                ByteBufCodecs.stringUtf8(96).apply(ByteBufCodecs.list(32)).encode(buffer, view.prizes());
                ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(16)).encode(buffer, view.places());
                ByteBufCodecs.stringUtf8(96).apply(ByteBufCodecs.list(16)).encode(buffer, view.hostRefusals());
                buffer.writeBoolean(view.show());
            },
            buffer -> new EventViewPayload(buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(64), buffer.readBoolean(),
                    buffer.readUtf(32), buffer.readUtf(32), buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(16), buffer.readBoolean(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt() - 1, buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readVarInt(),
                    Row.CODEC.apply(ByteBufCodecs.list(MOST_ROWS)).decode(buffer),
                    Match.CODEC.apply(ByteBufCodecs.list(MOST_ROWS)).decode(buffer),
                    Mine.CODEC.decode(buffer),
                    ByteBufCodecs.stringUtf8(96).apply(ByteBufCodecs.list(32)).decode(buffer),
                    ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(16)).decode(buffer),
                    ByteBufCodecs.stringUtf8(96).apply(ByteBufCodecs.list(16)).decode(buffer),
                    buffer.readBoolean()));

    public EventViewPayload {
        standings = List.copyOf(standings);
        pairings = List.copyOf(pairings);
        prizes = List.copyOf(prizes);
        places = List.copyOf(places);
        hostRefusals = List.copyOf(hostRefusals);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
