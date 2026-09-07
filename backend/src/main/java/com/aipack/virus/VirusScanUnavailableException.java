package com.aipack.virus;

import org.springframework.http.HttpStatus;

public class VirusScanUnavailableException extends RuntimeException {

    public VirusScanUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public VirusScanUnavailableException(String message) {
        super(message);
    }

    public String getCode() {
        return "VIRUS_SCAN_UNAVAILABLE";
    }

    public HttpStatus getStatus() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
