package dev.gathering.core.game;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A map a {@link GameState} owns outright, so the next state can share it rather than copy it.
 * <p>A state is an immutable value, and its constructor made it one by copying every map it was
 * given - which is the right thing to do with a map a caller might still hold. But almost every
 * state is built by the state before it, from maps it has just made and nobody else has seen, or
 * from its own maps unchanged. Copying those again bought nothing: a tap at a table of sixteen
 * hundred cards copied the card map twice, and changing whose turn it was copied every map there
 * is. Measured, a hundred and twenty-eight ordinary events at that size allocated 32 MB.
 * <p>So there are two ways in, and the difference between them is the whole of the safety
 * argument:
 *
 * <ul>
 *   <li>{@link #of} takes any map and copies it, unless it is already one of these - which can
 *       only have come from a state, and is already immutable.
 *   <li>{@link #adopt} takes a map without copying, and is package-private. Every call hands over
 *       a map built on the line before and not kept, so nothing can change it afterwards.
 * </ul>
 *
 * <p>Nothing reachable from outside can change one: the mutators are {@link AbstractMap}'s, which
 * refuse, and every view it hands out is an unmodifiable view. Insertion order is kept, because
 * states are written out by walking these.
 */
final class FrozenMap<K, V> extends AbstractMap<K, V> {

    private final LinkedHashMap<K, V> owned;
    private final Set<Entry<K, V>> entries;
    private final Set<K> keys;
    private final Collection<V> values;

    private FrozenMap(LinkedHashMap<K, V> owned) {
        this.owned = owned;
        Map<K, V> readOnly = Collections.unmodifiableMap(owned);
        this.entries = readOnly.entrySet();
        this.keys = readOnly.keySet();
        this.values = readOnly.values();
    }

    /** An immutable copy of any map, or the same map when it is one of these already. */
    static <K, V> Map<K, V> of(Map<K, V> source) {
        if (source instanceof FrozenMap<K, V> frozen) {
            return frozen;
        }
        return new FrozenMap<>(source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source));
    }

    /**
     * Takes ownership of a map without copying it.
     * <p>Only for a map the caller has just built and will not touch again. That is a promise
     * made by every call site in this package, and the reason this is not public.
     */
    static <K, V> Map<K, V> adopt(LinkedHashMap<K, V> built) {
        return new FrozenMap<>(built);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return entries;
    }

    @Override
    public Set<K> keySet() {
        return keys;
    }

    @Override
    public Collection<V> values() {
        return values;
    }

    @Override
    public V get(Object key) {
        return owned.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return owned.containsKey(key);
    }

    @Override
    public V getOrDefault(Object key, V fallback) {
        return owned.getOrDefault(key, fallback);
    }

    @Override
    public int size() {
        return owned.size();
    }

    @Override
    public boolean isEmpty() {
        return owned.isEmpty();
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        owned.forEach(action);
    }
}
