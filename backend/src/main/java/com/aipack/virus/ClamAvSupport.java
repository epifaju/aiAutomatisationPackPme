package com.aipack.virus;

import com.aipack.config.ProductionSecretsValidator;

/**
 * Activation ClamAV : vide / {@code auto} = on en production (P1.4), off sinon.
 * Fail-open : vide / {@code auto} = fail-open hors prod, fail-closed en production.
 */
public final class ClamAvSupport {

    private ClamAvSupport() {}

    public static boolean isEnabled(String enabled, String appEnv) {
        Boolean explicit = parseTriState(enabled);
        if (explicit != null) {
            return explicit;
        }
        return ProductionSecretsValidator.isProduction(appEnv);
    }

    public static boolean isFailOpen(String failOpen, String appEnv) {
        Boolean explicit = parseTriState(failOpen);
        if (explicit != null) {
            return explicit;
        }
        return !ProductionSecretsValidator.isProduction(appEnv);
    }

    private static Boolean parseTriState(String value) {
        if (value == null || value.isBlank() || "auto".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return Boolean.parseBoolean(value.trim());
    }
}
