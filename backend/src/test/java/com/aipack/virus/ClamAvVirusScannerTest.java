package com.aipack.virus;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClamAvVirusScannerTest {

    private FakeClamd clamd;

    @BeforeEach
    void startFakeClamd() throws IOException {
        clamd = FakeClamd.start();
    }

    @AfterEach
    void stop() throws IOException {
        clamd.close();
    }

    @Test
    void acceptsCleanStream() {
        ClamAvVirusScanner scanner = new ClamAvVirusScanner("127.0.0.1", clamd.port(), Duration.ofSeconds(2), false);
        assertThatCode(() -> scanner.assertClean("hello".getBytes(StandardCharsets.UTF_8), "ok.txt"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInfectedStream() {
        clamd.setInfected(true);
        ClamAvVirusScanner scanner = new ClamAvVirusScanner("127.0.0.1", clamd.port(), Duration.ofSeconds(2), false);
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
