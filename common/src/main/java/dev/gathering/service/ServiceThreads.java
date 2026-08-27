package dev.gathering.service;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Named daemon threads for the background services.
 *
 * <p>Daemon so a server shutdown is never held open by an in-flight fetch; named so a thread
 * dump says which service is doing what. One copy, because the card and collation services
 * each carried their own and a third service would have made a third.
 */
final class ServiceThreads {

    private ServiceThreads() {
    }

    static ThreadFactory named(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
