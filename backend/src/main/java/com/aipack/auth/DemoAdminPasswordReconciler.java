package com.aipack.auth;

import com.aipack.config.DemoDataSupport;
import com.aipack.identity.User;
import com.aipack.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Réécrit le hash pgcrypto du seed en BCrypt Spring — uniquement lorsque le seed démo est actif.
 */
@Component
@Order(100)
public class DemoAdminPasswordReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAdminPasswordReconciler.class);
    public static final String DEMO_EMAIL = "demo.admin@aipack.example";
    public static final String DEMO_PASSWORD = "DemoAdmin!2026";
    public static final String DEMO_USER_EMAIL = "demo.user@aipack.example";
    public static final String DEMO_USER_PASSWORD = "DemoUser!2026";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String appEnv;
    private final String demoSeedEnabled;

    public DemoAdminPasswordReconciler(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.env:development}") String appEnv,
            @Value("${app.demo-seed-enabled:}") String demoSeedEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.appEnv = appEnv;
        this.demoSeedEnabled = demoSeedEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!DemoDataSupport.isEnabled(demoSeedEnabled, appEnv)) {
            log.debug("Demo admin password reconciler skipped (demo seed disabled)");
            return;
        }
        userRepository.findAllByEmailIgnoreCase(DEMO_EMAIL).stream().findFirst().ifPresent(user -> reconcile(user, DEMO_PASSWORD));
        userRepository
                .findAllByEmailIgnoreCase(DEMO_USER_EMAIL)
                .stream()
                .findFirst()
                .ifPresent(user -> reconcile(user, DEMO_USER_PASSWORD));
    }

    private void reconcile(User user, String plaintext) {
        if (!passwordEncoder.matches(plaintext, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(plaintext));
            log.info("Demo account {} password rehashed with Spring BCrypt (demo seed only)", user.getEmail());
        }
    }
}
