package com.aipack.virus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Scanner no-op (ClamAV désactivé). */
public class NoOpVirusScanner implements VirusScanner {

    private static final Logger log = LoggerFactory.getLogger(NoOpVirusScanner.class);

    public NoOpVirusScanner() {
        log.debug("Virus scan disabled (NoOpVirusScanner)");
    }

    @Override
    public void assertClean(byte[] content, String filename) {
        // intentionally empty
    }
}
