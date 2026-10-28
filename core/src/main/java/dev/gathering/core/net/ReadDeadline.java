package dev.gathering.core.net;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A reply's body that gives up when the far end stops sending it.
 * <p>The JDK client's request timeout lasts until the headers arrive and not a moment after. Once
 * {@code send} has handed back the body, a read waits on a queue with no timeout of its own, and a
 * connection that goes quiet without closing - a laptop asleep, a Wi-Fi drop, a NAT that forgot the
 * flow - is waited on for ever: nothing on a quiet connection ever finds out the other end has gone.
 * Every download here runs on a thread that is the only one of its kind - the card worker, the
 * collation worker, the bulk refresh, the one set-symbol fetcher - so one stalled reply held up every
 * pack opening, deck import and shop restock on the server until it was restarted.
 * <p>So a body is read through this. A read that has waited this long for its next byte has the body
 * closed under it from a timer, which wakes it, and it throws: an {@link IOException}, so the fetcher's
 * own retry and backoff take over. The wait is per read, not for the whole reply, because a big file on
 * a slow line takes as long as it takes while bytes keep coming. And once the body has been closed
 * this way, nothing read from it counts as an ending: a reply cut short must never read as a reply that
 * was complete.
 * <p>One timer thread for every body, a daemon, so nothing here outlives the game or holds it open.
 */
public final class ReadDeadline {

    private static final ScheduledThreadPoolExecutor TIMER = timer();

    /** Not waiting on a read. {@link System#nanoTime} can be any long, but never two in a row of this. */
    private static final long NOT_WAITING = Long.MIN_VALUE;

    private ReadDeadline() {
    }

    /**
     * This body, giving up on any read that waits {@code quietest} for its next byte.
     *
     * @param body     the stream to read, which is closed if a read waits too long
     * @param quietest how long one read may wait before the reply counts as stalled
     */
    public static InputStream wrap(InputStream body, Duration quietest) {
        return new Watched(body, Math.max(1L, quietest.toNanos()));
    }

    private static ScheduledThreadPoolExecutor timer() {
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, work -> {
            Thread thread = new Thread(work, "gathering-read-deadline");
            thread.setDaemon(true);
            return thread;
        });
        // A canceled check leaves the queue at once, rather than when it would have run: every
        // body that closes in time cancels one, and they would otherwise pile up for half a minute.
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }

    private static final class Watched extends FilterInputStream {

        private final long quietestNanos;
        private final Object lock = new Object();

        /** When the read in progress began, or {@link #NOT_WAITING}. Written by the reader. */
        private volatile long waitingSince = NOT_WAITING;

        /** Whether the timer closed the body under a read that waited too long. */
        private volatile boolean stalled;

        private boolean closed;
        private ScheduledFuture<?> check;

        Watched(InputStream body, long quietestNanos) {
            super(body);
            this.quietestNanos = quietestNanos;
            checkIn(quietestNanos);
        }

        private void checkIn(long nanos) {
            synchronized (lock) {
                if (!closed) {
                    check = TIMER.schedule(this::check, nanos, TimeUnit.NANOSECONDS);
                }
            }
        }

        /** On the timer: gives up on a read that has waited too long, or looks again when one could have. */
        private void check() {
            long since = waitingSince;
            long waited = since == NOT_WAITING ? 0L : System.nanoTime() - since;
            if (since != NOT_WAITING && waited >= quietestNanos) {
                stalled = true;
                try {
                    // Closing the JDK's body from another thread is what it is built for: a blocked read
                    // is woken by the close and throws.
                    in.close();
                } catch (IOException | RuntimeException alreadyGone) {
                    // Closed is closed.
                }
                return;
            }
            checkIn(quietestNanos - waited);
        }

        @Override
        public int read() throws IOException {
            int read;
            waitingSince = System.nanoTime();
            try {
                read = in.read();
            } catch (IOException failed) {
                throw stalled ? stalled(failed) : failed;
            } finally {
                waitingSince = NOT_WAITING;
            }
            if (stalled) {
                throw stalled(null);
            }
            return read;
        }

        @Override
        public int read(byte[] into, int offset, int length) throws IOException {
            int read;
            waitingSince = System.nanoTime();
            try {
                read = in.read(into, offset, length);
            } catch (IOException failed) {
                throw stalled ? stalled(failed) : failed;
            } finally {
                waitingSince = NOT_WAITING;
            }
            if (stalled) {
                throw stalled(null);
            }
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            long skipped;
            waitingSince = System.nanoTime();
            try {
                skipped = in.skip(count);
            } catch (IOException failed) {
                throw stalled ? stalled(failed) : failed;
            } finally {
                waitingSince = NOT_WAITING;
            }
            if (stalled) {
                throw stalled(null);
            }
            return skipped;
        }

        @Override
        public void close() throws IOException {
            synchronized (lock) {
                closed = true;
                if (check != null) {
                    check.cancel(false);
                }
            }
            in.close();
        }

        private FetchException stalled(IOException cause) {
            String said = "the reply stopped arriving: nothing came for "
                    + Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(quietestNanos)) + " seconds";
            return cause == null ? new FetchException(said, -1) : new FetchException(said, cause);
        }
    }
}
