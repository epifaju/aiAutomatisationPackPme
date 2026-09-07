package com.aipack.virus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client clamd protocole {@code zINSTREAM} (null-terminated).
 *
 * @see <a href="https://docs.clamav.net/manual/Usage/Scanning.html#clamd">ClamAV clamd</a>
 */
public class ClamAvVirusScanner implements VirusScanner {

    private static final Logger log = LoggerFactory.getLogger(ClamAvVirusScanner.class);
    private static final int CHUNK_SIZE = 2048;

    private final String host;
    private final int port;
    private final Duration timeout;
    private final boolean failOpen;

    public ClamAvVirusScanner(ClamAvProperties properties) {
        this.host = properties.hostOrDefault();
        this.port = properties.portOrDefault();
        this.timeout = properties.timeoutOrDefault();
        this.failOpen = properties.failOpen();
    }

    /** Package-visible for tests with custom endpoint. */
    ClamAvVirusScanner(String host, int port, Duration timeout, boolean failOpen) {
        this.host = host;
        this.port = port;
        this.timeout = timeout;
        this.failOpen = failOpen;
    }

    @Override
    public void assertClean(byte[] content, String filename) {
        if (content == null || content.length == 0) {
            return;
        }
        try {
            String reply = scan(content);
            if (reply == null || reply.isBlank()) {
                handleUnavailable("Réponse ClamAV vide", null);
                return;
            }
            String normalized = reply.trim();
            if (normalized.endsWith("OK")) {
                return;
            }
            if (normalized.contains("FOUND")) {
                String signature = extractSignature(normalized);
                log.warn("Malware détecté (file={}, signature={})", filename, signature);
                throw new MalwareDetectedException(signature);
            }
            handleUnavailable("Réponse ClamAV inattendue: " + normalized, null);
        } catch (MalwareDetectedException | VirusScanUnavailableException ex) {
            throw ex;
        } catch (IOException ex) {
            handleUnavailable("ClamAV injoignable", ex);
        }
    }

    private void handleUnavailable(String message, Throwable cause) {
        if (failOpen) {
            log.warn("Scan antivirus indisponible (fail-open): {}", message);
            return;
        }
        throw new VirusScanUnavailableException(message, cause);
    }

    private String scan(byte[] content) throws IOException {
        int soTimeout = (int) Math.min(timeout.toMillis(), Integer.MAX_VALUE);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), soTimeout);
            socket.setSoTimeout(soTimeout);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));

            int offset = 0;
            while (offset < content.length) {
                int len = Math.min(CHUNK_SIZE, content.length - offset);
                out.write(ByteBuffer.allocate(4).putInt(len).array());
                out.write(content, offset, len);
                offset += len;
            }
            out.write(ByteBuffer.allocate(4).putInt(0).array());
            out.flush();

            return readNullTerminated(in);
        }
    }

    private static String readNullTerminated(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == 0) {
                break;
            }
            buffer.write(b);
        }
        return buffer.toString(StandardCharsets.US_ASCII);
    }

    private static String extractSignature(String reply) {
        // e.g. "stream: Eicar-Test-Signature FOUND"
        String withoutFound = reply.replace(" FOUND", "").trim();
        int colon = withoutFound.indexOf(':');
        if (colon >= 0 && colon + 1 < withoutFound.length()) {
            return withoutFound.substring(colon + 1).trim();
        }
        return withoutFound;
    }
}
