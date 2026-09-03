package com.aipack.config;

import com.aipack.storage.InMemoryObjectStorage;
import com.aipack.storage.MinioObjectStorage;
import com.aipack.storage.ObjectStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StorageProperties.class, DocumentProperties.class})
public class StorageConfig {

    @Bean
    ObjectStorage objectStorage(StorageProperties properties) {
        if ("memory".equalsIgnoreCase(properties.providerOrDefault())) {
            return new InMemoryObjectStorage();
        }
        return new MinioObjectStorage(properties);
    }
}
