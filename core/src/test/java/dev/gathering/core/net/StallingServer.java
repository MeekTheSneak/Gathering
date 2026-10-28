package dev.gathering.core.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A far end on this machine that starts a reply and then goes quiet, without closing.
 * <p>What a laptop asleep, a Wi-Fi drop or a NAT that forgot the flow look like from the asking side:
 * the headers came, some of the body came, and then nothing - no more bytes and no end. A real socket
 * rather than a fake transport, because the fault is in how the JDK's own client waits on its body, and
 * a fake has no such wait to get wrong.
 * <p>Each connection takes the next reply off the script, and every one after the script runs out is
 * answered properly. A stalled connection is held open until this is closed.
 */
public final class StallingServer implements AutoCloseable {

    /** What one connection is answered with. */
    public enum Reply {
        /** Promises a hundred thousand bytes, sends ten, and stops. */
        STALL,
        /** Promises a gzipped body and sends none of it, not even its header. */
        STALL_BEFORE_THE_GZIP_HEADER,
        /** Answers "ok", whole, and closes. */
        ANSWER
    }

    private final ServerSocket listening;
    private final Deque<Reply> script;
    private final CountDownLatch released = new CountDownLatch(1);
    private final List<Socket> open = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger connections = new AtomicInteger();

    public StallingServer(Reply... script) throws IOException {
        this.listening = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        this.script = new ArrayDeque<>(List.of(script));
        Thread accepting = new Thread(this::accept, "stalling-server");
        accepting.setDaemon(true);
        accepting.start();
    }

    /** Where to ask. */
    public String url() {
        return "http://127.0.0.1:" + listening.getLocalPort() + "/reply";
    }

    /** How many connections have been made to it. */
    public int connections() {
        return connections.get();
    }

    private void accept() {
        while (!listening.isClosed()) {
            try {
                Socket socket = listening.accept();
                open.add(socket);
                connections.incrementAndGet();
                Reply reply;
                synchronized (script) {
                    reply = script.isEmpty() ? Reply.ANSWER : script.removeFirst();
                }
                Thread answering = new Thread(() -> answer(socket, reply), "stalling-server-reply");
                answering.setDaemon(true);
                answering.start();
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void answer(Socket socket, Reply reply) {
        try (socket) {
            BufferedReader request = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String line;
            while ((line = request.readLine()) != null && !line.isEmpty()) {
                // The request's own headers, read and thrown away.
            }
            OutputStream out = socket.getOutputStream();
            switch (reply) {
                case STALL -> out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                        + "Content-Length: 100000\r\n\r\n0123456789").getBytes(StandardCharsets.ISO_8859_1));
                case STALL_BEFORE_THE_GZIP_HEADER -> out.write(("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\n"
                        + "Content-Length: 100000\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                case ANSWER -> out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                        .getBytes(StandardCharsets.ISO_8859_1));
            }
            out.flush();
            if (reply != Reply.ANSWER) {
                // Open and silent: no FIN, no RST, which is the whole of the fault.
                released.await();
            }
        } catch (IOException | InterruptedException gone) {
            // The asking side gave up and closed, which is what it is meant to do.
        }
    }

    @Override
    public void close() throws IOException {
        released.countDown();
        listening.close();
        synchronized (open) {
            for (Socket socket : open) {
                socket.close();
            }
        }
    }
}
