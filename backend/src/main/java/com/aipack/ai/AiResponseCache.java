package com.aipack.ai;

import java.util.Optional;

public interface AiResponseCache {

    Optional<String> get(String key);

    void put(String key, String value);
}
