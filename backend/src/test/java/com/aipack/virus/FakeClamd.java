package com.aipack.virus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Mini clamd zINSTREAM pour les tests (réponse OK ou FOUND). */
public final class FakeClamd implements AutoCloseable {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ServerSocket serverSocket;
    private final AtomicBoolean infected = new AtomicBoolean(false);

    private FakeClamd(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
        executor.submit(this::acceptLoop);
    }

    public static FakeClamd start() throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        return new FakeClamd(serverSocket);
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public void setInfected(boolean value) {
        infected.set(value);
    }

    private void acceptLoop() {
        try {
            while (!serverSocket.isClosed()) {
                try (Socket client = serverSocket.accept()) {
                    InputStream in = client.getInputStream();
                    OutputStream out = client.getOutputStream();
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
                    String reply = infected.get() ? "stream: Eicar-Test-Signature FOUND\0" : "stream: OK\0";
                    out.write(reply.getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (IOException ignored) {
                    // server closed or client disconnected
                }
            }
        } catch (Exception ignored) {
            // exit
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        executor.shutdownNow();
    }
}
