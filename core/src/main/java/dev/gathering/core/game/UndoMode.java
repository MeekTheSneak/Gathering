package dev.gathering.core.game;

/**
 * How much rewinding a table allows. Configurable per table with a server default.
 *
 * <p>In every mode the information boundary is hard. The log marks the events that let
 * somebody see something, and no rewind crosses one without everybody agreeing, because a
 * seen card cannot be un-seen. That is not a setting.
 */
public enum UndoMode {

    /** Any rewind needs every seated player's consent. The competitive setting. */
    UNANIMOUS,

    /**
     * A player rewinds their own most recent actions instantly. The casual setting, and the
     * shipped default - most undos are a misclick, and asking three friends for permission to
     * un-tap a land is worse than the misclick.
     */
    FREE,

    /** No rewinding at all. */
    OFF;

    public static UndoMode shippedDefault() {
        return FREE;
    }
}
