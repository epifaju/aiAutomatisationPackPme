package com.aipack.storage;

public interface ObjectStorage {

    StoredObject put(String key, byte[] content, String contentType);

    byte[] get(String key);

    void delete(String key);
}
