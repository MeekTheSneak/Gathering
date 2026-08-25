package dev.gathering.core.booster;

/**
 * What is wrong with a booster file, in words an admin can act on.
 *
 * <p>Checked rather than unchecked, because a bad file is an ordinary thing that happens to
 * somebody hand-editing JSON and the caller has a real decision to make about it: say so and
 * carry on with the other files, rather than fail to load.
 *
 * <p>The message always says <em>where</em>. "Not a number" is useless; "sheet 'common',
 * card 3: weight is not a number" is a line somebody can go and look at.
 */
public class BoosterCodecException extends Exception {

    private static final long serialVersionUID = 1L;

    public BoosterCodecException(String message) {
        super(message);
    }
}
