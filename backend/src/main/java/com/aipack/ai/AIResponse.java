package com.aipack.ai;

public record AIResponse(
        String text, String provider, String model, int latencyMs, boolean cacheHit, String status) {

    public static AIResponse unavailable(String provider, String model, int latencyMs) {
        return new AIResponse("", provider, model, latencyMs, false, "AI_UNAVAILABLE");
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
