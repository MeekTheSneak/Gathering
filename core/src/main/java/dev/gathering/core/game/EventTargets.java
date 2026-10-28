package dev.gathering.core.game;

import dev.gathering.core.game.event.GameEvent;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What an event names: which seats, and which cards.
 * <p>Read off the event's own record components rather than written out case by case. Every
 * event is a record, and a seat it acts on is a {@link SeatId} component of that record - or
 * the seat inside a {@link ZoneRef} one. That matters because the alternative is a switch with
 * forty-two arms which the next event added would quietly not appear in: the rules that use
 * this - "every seat an event names is a seat at this table", "nobody touches a card in
 * somebody else's hand" - are the kind that must hold for events nobody has written yet.
 * <p>An audit reproduced both halves of what that costs. A token made for seat ninety-nine of
 * a two-player game was accepted and landed in a zone no view is built for; commander tax was
 * charged to a card instance that never existed. Neither event had a case anywhere that
 * checked, because checking was something each new event had to remember to ask for.
 * <p>The accessors are looked up once per event class and kept. Reflection on a hot path is
 * worth a sentence: a fold walks a handful of accessors per event, the map is read far more
 * often than written, and a replay of four thousand events pays for four dozen lookups.
 */
public final class EventTargets {

    /** Per event class, the accessors that answer with a seat - directly or through a zone. */
    private static final Map<Class<?>, List<Method>> SEAT_PARTS = new ConcurrentHashMap<>();

    /** Per event class, the accessors that answer with a card, or a list of them. */
    private static final Map<Class<?>, List<Method>> CARD_PARTS = new ConcurrentHashMap<>();

    private EventTargets() {
    }

    /** Every seat this event names, in the order its components declare them. */
    public static List<SeatId> seatsNamedBy(GameEvent event) {
        List<SeatId> found = new ArrayList<>(2);
        for (Method part : partsOf(event, SEAT_PARTS, SeatId.class, ZoneRef.class)) {
            Object value = read(part, event);
            if (value instanceof SeatId seat) {
                found.add(seat);
            } else if (value instanceof ZoneRef ref) {
                found.add(ref.seat());
            }
        }
        return found;
    }

    /**
     * Every card instance this event names, lists included.
     * <p>Lists because a crafted {@code LibraryReordered} or {@code HandSorted} names its
     * cards in one, and a rule about which cards may be touched has to see those too.
     */
    public static List<CardInstanceId> cardsNamedBy(GameEvent event) {
        List<CardInstanceId> found = new ArrayList<>(2);
        for (Method part : partsOf(event, CARD_PARTS, CardInstanceId.class, List.class)) {
            Object value = read(part, event);
            if (value instanceof CardInstanceId card) {
                found.add(card);
            } else if (value instanceof List<?> many) {
                for (Object each : many) {
                    if (each instanceof CardInstanceId card) {
                        found.add(card);
                    }
                }
            }
        }
        return found;
    }

    private static List<Method> partsOf(
            GameEvent event, Map<Class<?>, List<Method>> cache, Class<?> wanted, Class<?> alsoWanted) {
        return cache.computeIfAbsent(event.getClass(), type -> {
            List<Method> parts = new ArrayList<>(2);
            RecordComponent[] components = type.getRecordComponents();
            if (components == null) {
                return List.of();
            }
            for (RecordComponent component : components) {
                if (wanted.isAssignableFrom(component.getType())
                        || alsoWanted.isAssignableFrom(component.getType())) {
                    parts.add(component.getAccessor());
                }
            }
            return List.copyOf(parts);
        });
    }

    private static Object read(Method accessor, GameEvent event) {
        try {
            return accessor.invoke(event);
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            // An accessor that will not answer is not a target. Refusing the whole event here
            // would turn a reflection problem into a game that cannot be played.
            return null;
        }
    }
}
