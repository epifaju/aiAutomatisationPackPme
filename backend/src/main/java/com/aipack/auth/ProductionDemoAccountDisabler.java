package com.aipack.auth;

import com.aipack.config.DemoDataSupport;
import com.aipack.config.ProductionSecretsValidator;
import com.aipack.identity.User;
import com.aipack.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * En production (ou si DEMO_SEED_ENABLED=false), désactive le compte démo s’il existe encore en base.
 */
@Component
@Order(101)
public class ProductionDemoAccountDisabler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionDemoAccountDisabler.class);

    private final UserRepository userRepository;
    private final String appEnv;
    private final String demoSeedEnabled;

    public ProductionDemoAccountDisabler(
            UserRepository userRepository,
            @Value("${app.env:development}") String appEnv,
            @Value("${app.demo-seed-enabled:}") String demoSeedEnabled) {
        this.userRepository = userRepository;
        this.appEnv = appEnv;
        this.demoSeedEnabled = demoSeedEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean mustDisable = ProductionSecretsValidator.isProduction(appEnv)
                || DemoDataSupport.isExplicitlyDisabled(demoSeedEnabled);
        if (!mustDisable) {
            return;
        }
        userRepository.findAllByEmailIgnoreCase(DemoAdminPasswordReconciler.DEMO_EMAIL).forEach(this::disableIfNeeded);
        userRepository
                .findAllByEmailIgnoreCase(DemoAdminPasswordReconciler.DEMO_USER_EMAIL)
                .forEach(this::disableIfNeeded);
    }

    private void disableIfNeeded(User user) {
        if (user.isEnabled()) {
            user.setEnabled(false);
            log.warn(
                    "Compte démo {} désactivé (APP_ENV={}, DEMO_SEED_ENABLED={})",
                    user.getEmail(),
                    appEnv,
                    demoSeedEnabled == null || demoSeedEnabled.isBlank() ? "auto" : demoSeedEnabled.trim());
        }
    }
}
