package com.aipack.ai;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedisAiResponseCache implements AiResponseCache {

    private static final Logger log = LoggerFactory.getLogger(RedisAiResponseCache.class);
    private static final long RETRY_AFTER_MS = 30_000L;

    private final String host;
    private final int port;
    private final long ttlSeconds;
    private final AtomicBoolean disabled = new AtomicBoolean(false);
    private final Object lock = new Object();

    private volatile long disabledUntilEpochMs;
    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;

    public RedisAiResponseCache(String host, int port, Duration ttl) {
        this.host = host;
        this.port = port;
        this.ttlSeconds = Math.max(1, ttl == null ? 3600 : ttl.toSeconds());
    }

    @Override
    public Optional<String> get(String key) {
        StatefulRedisConnection<String, String> conn = connection();
        if (conn == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(conn.sync().get(key));
        } catch (RuntimeException ex) {
            disable(ex);
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, String value) {
        if (value == null) {
            return;
        }
        StatefulRedisConnection<String, String> conn = connection();
        if (conn == null) {
            return;
        }
        try {
            conn.sync().setex(key, ttlSeconds, value);
        } catch (RuntimeException ex) {
            disable(ex);
        }
    }

    @PreDestroy
    public void close() {
        synchronized (lock) {
            closeQuietly();
        }
    }

    private StatefulRedisConnection<String, String> connection() {
        if (disabled.get() && System.currentTimeMillis() < disabledUntilEpochMs) {
            return null;
        }
        synchronized (lock) {
            if (disabled.get() && System.currentTimeMillis() < disabledUntilEpochMs) {
                return null;
            }
            if (connection != null) {
                return connection;
            }
            try {
                closeQuietly();
                RedisURI uri = RedisURI.builder()
                        .withHost(host)
                        .withPort(port)
                        .withTimeout(Duration.ofMillis(250))
                        .build();
                client = RedisClient.create(uri);
                client.setDefaultTimeout(Duration.ofMillis(250));
                connection = client.connect();
                disabled.set(false);
                disabledUntilEpochMs = 0L;
                return connection;
            } catch (RuntimeException ex) {
                disable(ex);
                return null;
            }
        }
    }

    private void disable(RuntimeException ex) {
        disabledUntilEpochMs = System.currentTimeMillis() + RETRY_AFTER_MS;
        if (disabled.compareAndSet(false, true)) {
            log.warn(
                    "Cache Redis IA indisponible, poursuite sans cache (nouvel essai dans {}s) : {}",
                    RETRY_AFTER_MS / 1000,
                    ex.getMessage());
        }
        synchronized (lock) {
            closeQuietly();
        }
    }

    private void closeQuietly() {
        if (connection != null) {
            try {
                connection.close();
            } catch (RuntimeException ignored) {
                // ignore
            }
            connection = null;
        }
        if (client != null) {
            try {
                client.shutdown();
            } catch (RuntimeException ignored) {
                // ignore
            }
            client = null;
        }
    }
}
