package com.aipack.storage;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryObjectStorage implements ObjectStorage {

    private final ConcurrentHashMap<String, byte[]> objects = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> contentTypes = new ConcurrentHashMap<>();

    @Override
    public StoredObject put(String key, byte[] content, String contentType) {
        byte[] copy = content.clone();
        objects.put(key, copy);
        contentTypes.put(key, contentType);
        return new StoredObject(key, contentType, copy.length);
    }

    @Override
    public byte[] get(String key) {
        byte[] content = objects.get(key);
        if (content == null) {
            throw new StorageException("Objet introuvable : " + key);
        }
        return content.clone();
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
        contentTypes.remove(key);
    }
}
