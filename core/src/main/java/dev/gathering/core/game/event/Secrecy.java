package dev.gathering.core.game.event;

/**
 * Where an event's content is allowed to live at rest.
 * <p>The hidden-information lifecycle has three stages and one principle: unauthorized
 * parties never hold secrets, sanitized or otherwise.
 *
 * <ul>
 *   <li><b>Live</b> - hidden identity goes only to entitled clients. Everyone else gets the
 *       event's public log line, which has no content in it to extract.</li>
 *   <li><b>At rest</b> - {@link #SECRET} events are written to a separate encrypted stream
 *       whose key is sealed server-side, so a save file opened mid-session with external
 *       tools yields ciphertext.</li>
 *   <li><b>At session end</b> - the streams are decrypted and merged into the replay, which
 *       is the single moment scries, searches and hands become record.</li>
 * </ul>
 */
public enum Secrecy {

    /** Content is safe for anyone: a life change, a tap, a phase advance. */
    PUBLIC,

    /**
     * Content names cards somebody is not entitled to see: a deck's order, a scry's
     * resolution. Goes to the sealed stream and is disclosed only by the replay.
     */
    SECRET;

    public boolean isSecret() {
        return this == SECRET;
    }
}
