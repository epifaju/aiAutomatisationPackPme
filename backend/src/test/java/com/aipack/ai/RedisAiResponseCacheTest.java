package com.aipack.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisURI;
import org.junit.jupiter.api.Test;

class RedisAiResponseCacheTest {

    @Test
    void buildUriOmitsPasswordWhenBlank() {
        RedisURI uri = RedisAiResponseCache.buildUri("localhost", 6379, "");
        assertThat(uri.getPassword()).isNull();
        assertThat(uri.getHost()).isEqualTo("localhost");
        assertThat(uri.getPort()).isEqualTo(6379);
    }

    @Test
    void buildUriSetsPasswordWhenProvided() {
        RedisURI uri = RedisAiResponseCache.buildUri("redis", 6379, "s3cret-redis");
        assertThat(uri.getPassword()).isEqualTo("s3cret-redis".toCharArray());
        assertThat(uri.getHost()).isEqualTo("redis");
    }
}
