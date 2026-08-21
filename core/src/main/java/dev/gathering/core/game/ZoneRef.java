package dev.gathering.core.game;

/**
 * One seat's copy of one zone - "seat 2's graveyard".
 *
 * <p>For the battlefield the seat is where the card currently sits on the table, which is
 * how control changes are modelled: stealing a creature is moving it to your side. That is
 * separate from, and never overwrites, the card's owner.
 */
public record ZoneRef(SeatId seat, Zone zone) {

    public ZoneRef {
        if (seat == null || zone == null) {
            throw new IllegalArgumentException("A zone reference needs both a seat and a zone");
        }
    }

    public static ZoneRef of(SeatId seat, Zone zone) {
        return new ZoneRef(seat, zone);
    }

    public boolean isHidden() {
        return zone.isHidden();
    }

    @Override
    public String toString() {
        return seat + "/" + zone.name().toLowerCase(java.util.Locale.ROOT);
    }
}
