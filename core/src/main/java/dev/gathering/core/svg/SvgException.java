package dev.gathering.core.svg;

/**
 * What could not be read in an SVG, and where.
 *
 * <p>Checked, because a symbol that will not draw is an ordinary thing to have to carry on
 * past - a pack with no symbol on it is still a pack - and the caller has a real decision to
 * make about it.
 */
public class SvgException extends Exception {

    private static final long serialVersionUID = 1L;

    public SvgException(String message) {
        super(message);
    }

    public SvgException(String message, Throwable cause) {
        super(message, cause);
    }
}
