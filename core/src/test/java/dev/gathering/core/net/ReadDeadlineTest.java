package dev.gathering.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a body read through {@link ReadDeadline} gives up on, and what it does not.
 * <p>Per read and not per reply: a big file on a slow line is read whole however long it takes, so
 * long as each piece of it comes inside the deadline. And a body cut off by the deadline never reads
 * as a body that was complete.
 */
@DisplayName("A body read with a deadline")
class ReadDeadlineTest {

    private static final Duration LONGEST = Duration.ofSeconds(15);

    /**
     * The JDK's body, as far as this is concerned: a read waits until a byte comes or the body is
     * closed, and closing it from another thread wakes the read.
     */
    private static final class Silent extends InputStream {
        private final CountDownLatch closed = new CountDownLatch(1);
        private final boolean endsWhenClosed;
        final AtomicInteger closings = new AtomicInteger();

        /** @param endsWhenClosed whether a read woken by the close says the body ended, rather than throwing */
        Silent(boolean endsWhenClosed) {
            this.endsWhenClosed = endsWhenClosed;
        }

        @Override
        public int read() throws IOException {
            try {
                closed.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", interrupted);
            }
            if (endsWhenClosed) {
                return -1;
            }
            throw new IOException("closed");
        }

        @Override
        public void close() {
            closings.incrementAndGet();
            closed.countDown();
        }
    }

    /** Forty bytes, a twentieth of a second apart: two seconds in all, against a deadline of one. */
    private static final class Trickle extends InputStream {
        private int sent;

        @Override
        public int read() throws IOException {
            if (sent == 40) {
                return -1;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", interrupted);
            }
            return 'a' + sent++ % 20;
        }

        /**
         * A byte at a time, as the JDK's body hands over what has arrived rather than waiting to fill
         * the buffer. The default would wait for all forty in one read, which is a read waiting two
         * seconds - and that is exactly what the deadline is for.
         */
        @Override
        public int read(byte[] into, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            int one = read();
            if (one < 0) {
                return -1;
            }
            into[offset] = (byte) one;
            return 1;
        }
    }

    @Test
    @DisplayName("gives up on a read that waits past the deadline")
    void aReadThatWaitsTooLongGivesUp() {
        Silent body = new Silent(false);
        InputStream watched = ReadDeadline.wrap(body, Duration.ofMillis(200));

        assertTimeoutPreemptively(LONGEST, () -> assertThatThrownBy(watched::read)
                .isInstanceOf(FetchException.class)
                .hasMessageContaining("stopped arriving"));
        assertThat(body.closings.get()).isPositive();
    }

    /**
     * A body that answers a close with an ending rather than a failure is still a reply cut short,
     * and saying it ended would hand somebody half a file as though it were all of it.
     */
    @Test
    @DisplayName("never reads a reply it cut off as one that ended")
    void aReplyCutOffIsNeverAnEnding() {
        InputStream watched = ReadDeadline.wrap(new Silent(true), Duration.ofMillis(200));

        assertTimeoutPreemptively(LONGEST, () -> assertThatThrownBy(watched::readAllBytes)
                .isInstanceOf(FetchException.class));
    }

    @Test
    @DisplayName("reads a slow body whole, however long it takes, while each piece comes in time")
    void aSlowBodyThatKeepsComingIsReadWhole() throws IOException {
        InputStream watched = ReadDeadline.wrap(new Trickle(), Duration.ofSeconds(1));

        byte[] read = assertTimeoutPreemptively(LONGEST, () -> watched.readAllBytes());

        assertThat(new String(read, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("abcdefghijklmnopqrstabcdefghijklmnopqrst");
        watched.close();
    }

    /** The deadline is a read waiting, not a body held: one put down for a while is still there to read. */
    @Test
    @DisplayName("does not give up on a body nobody is reading yet")
    void aBodyNobodyIsReadingIsLeftAlone() throws Exception {
        InputStream watched = ReadDeadline.wrap(new java.io.ByteArrayInputStream("there".getBytes(
                java.nio.charset.StandardCharsets.US_ASCII)), Duration.ofMillis(100));

        Thread.sleep(400);

        assertThat(new String(watched.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("there");
        watched.close();
    }
}
