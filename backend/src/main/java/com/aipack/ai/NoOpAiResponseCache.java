package com.aipack.ai;

import java.util.Optional;

public class NoOpAiResponseCache implements AiResponseCache {

    @Override
    public Optional<String> get(String key) {
        return Optional.empty();
    }

    @Override
    public void put(String key, String value) {
        // cache désactivé
    }
}
