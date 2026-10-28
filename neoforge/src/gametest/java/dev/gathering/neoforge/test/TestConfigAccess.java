package dev.gathering.neoforge.test;

/**
 * {@link TestConfig#run} for the compatibility tests, which live in their own package.
 * <p>TestConfig stays package-private so the ordinary suites keep reaching it the one way; this
 * is the single door out of the package, and it opens onto the synchronous form only, where a
 * setting cannot outlive the call and be seen by a test running beside it.
 */
public final class TestConfigAccess {

    private TestConfigAccess() {
    }

    public static void run(String text, Runnable body) {
        TestConfig.run(text, body);
    }
}
