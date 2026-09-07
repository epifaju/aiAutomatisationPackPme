package com.aipack.config;

/**
 * Contrôle l’activation des données de démo (seed Flyway + reconciler mot de passe).
 * Désactivé automatiquement si {@code APP_ENV=production|prod}, sauf surcharge explicite.
 */
public final class DemoDataSupport {

    private DemoDataSupport() {}

    /**
     * @param demoSeedEnabled valeur de {@code app.demo-seed-enabled} (vide = auto)
     * @param appEnv valeur de {@code app.env}
     */
    public static boolean isEnabled(String demoSeedEnabled, String appEnv) {
        if (demoSeedEnabled != null && !demoSeedEnabled.isBlank()) {
            return Boolean.parseBoolean(demoSeedEnabled.trim());
        }
        return !ProductionSecretsValidator.isProduction(appEnv);
    }

    public static boolean isExplicitlyDisabled(String demoSeedEnabled) {
        if (demoSeedEnabled == null || demoSeedEnabled.isBlank()) {
            return false;
        }
        return !Boolean.parseBoolean(demoSeedEnabled.trim());
    }
}
