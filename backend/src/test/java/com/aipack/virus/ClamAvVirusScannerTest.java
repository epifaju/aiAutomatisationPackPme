package com.aipack.virus;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClamAvVirusScannerTest {

    private ExecutorService executor;
    private ServerSocket serverSocket;
    private int port;
    private final AtomicBoolean replyInfected = new AtomicBoolean(false);

    @BeforeEach
    void startFakeClamd() throws IOException {
        executor = Executors.newSingleThreadExecutor();
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        port = serverSocket.getLocalPort();
        executor.submit(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    try (Socket client = serverSocket.accept()) {
                        InputStream in = client.getInputStream();
                        OutputStream out = client.getOutputStream();
                        // read zINSTREAM\0
                        ByteBuffer cmd = ByteBuffer.allocate(16);
                        while (cmd.position() < 10) {
                            int b = in.read();
                            if (b < 0) {
                                break;
                            }
                            cmd.put((byte) b);
                            if (b == 0) {
                                break;
                            }
                        }
                        // drain chunks until size 0
                        while (true) {
                            byte[] sizeBuf = in.readNBytes(4);
                            if (sizeBuf.length < 4) {
                                break;
                            }
                            int size = ByteBuffer.wrap(sizeBuf).getInt();
                            if (size == 0) {
                                break;
                            }
                            in.readNBytes(size);
                        }
                        String reply = replyInfected.get()
                                ? "stream: Eicar-Test-Signature FOUND\0"
                                : "stream: OK\0";
                        out.write(reply.getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    } catch (IOException ignored) {
                        // server closed or client disconnected
                    }
                }
            } catch (Exception ignored) {
                // exit
            }
        });
    }

    @AfterEach
    void stop() throws IOException {
        serverSocket.close();
        executor.shutdownNow();
    }

    @Test
    void acceptsCleanStream() {
        ClamAvVirusScanner scanner = new ClamAvVirusScanner("127.0.0.1", port, Duration.ofSeconds(2), false);
        assertThatCode(() -> scanner.assertClean("hello".getBytes(StandardCharsets.UTF_8), "ok.txt"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInfectedStream() {
        replyInfected.set(true);
        ClamAvVirusScanner scanner = new ClamAvVirusScanner("127.0.0.1", port, Duration.ofSeconds(2), false);
        assertThatThrownBy(() -> scanner.assertClean("X5O!".getBytes(StandardCharsets.UTF_8), "eicar.txt"))
                .isInstanceOf(MalwareDetectedException.class)
                .hasMessageContaining("Eicar-Test-Signature");
    }

    @Test
    void failOpenAllowsWhenClamdDown() throws IOException {
        int closedPort;
        try (ServerSocket unused = new ServerSocket(0)) {
            closedPort = unused.getLocalPort();
        }
        ClamAvVirusScanner scanner = new ClamAvVirusScanner("127.0.0.1", closedPort, Duration.ofMillis(200), true);
        assertThatCode(() -> scanner.assertClean("data".getBytes(StandardCharsets.UTF_8), "a.txt"))
                .doesNotThrowAnyException();
    }

    @Test
    void failClosedRejectsWhenClamdDown() throws IOException {
        int closedPort;
        try (ServerSocket unused = new ServerSocket(0)) {
            closedPort = unused.getLocalPort();
        }
        ClamAvVirusScanner scanner = new ClamAvVirusScanner("127.0.0.1", closedPort, Duration.ofMillis(200), false);
        assertThatThrownBy(() -> scanner.assertClean("data".getBytes(StandardCharsets.UTF_8), "a.txt"))
                .isInstanceOf(VirusScanUnavailableException.class);
    }
}
