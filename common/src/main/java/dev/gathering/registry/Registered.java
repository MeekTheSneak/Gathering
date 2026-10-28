package dev.gathering.registry;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A registry entry the platform bootstrap binds exactly once.
 * <p>Registration lives in the loader modules, but the rest of the mod needs to name the
 * things that get registered. This holder is the seam.
 * <p>It binds a {@link Supplier} rather than a value so the bootstrap can hand over
 * NeoForge's {@code DeferredHolder} in the mod constructor, before the entry actually
 * exists. That sidesteps the lifecycle question entirely: nothing has to be re-bound after
 * registration finishes, and reading too early fails loudly - which is the classic
 * "registry object not present" bug, named at its cause instead of surfacing as a null
 * three frames away.
 */
public final class Registered<T> implements Supplier<T> {

    private final String name;
    private volatile Supplier<T> source;

    public Registered(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public void bind(Supplier<T> newSource) {
        Objects.requireNonNull(newSource, "newSource");
        if (this.source != null) {
            throw new IllegalStateException("Registered entry '" + name + "' is already bound");
        }
        this.source = newSource;
    }

    /** Convenience for loaders whose registration returns the object directly. */
    public void bindValue(T value) {
        Objects.requireNonNull(value, "value");
        bind(() -> value);
    }

    @Override
    public T get() {
        Supplier<T> current = source;
        if (current == null) {
            throw new IllegalStateException(
                    "Registered entry '" + name + "' was read before the platform bootstrap bound it. "
                            + "Bind it in the mod constructor, and never query a registry during registration.");
        }
        return Objects.requireNonNull(current.get(), () -> "Registered entry '" + name + "' resolved to null");
    }

    public boolean isBound() {
        return source != null;
    }

    public String entryName() {
        return name;
    }
}
