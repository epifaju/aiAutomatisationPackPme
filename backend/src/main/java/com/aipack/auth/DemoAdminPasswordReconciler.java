package com.aipack.auth;

import com.aipack.identity.User;
import com.aipack.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"dev", "development", "test"})
public class DemoAdminPasswordReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAdminPasswordReconciler.class);
    static final String DEMO_EMAIL = "demo.admin@aipack.example";
    static final String DEMO_PASSWORD = "DemoAdmin!2026";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoAdminPasswordReconciler(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findAllByEmailIgnoreCase(DEMO_EMAIL).stream().findFirst().ifPresent(this::reconcile);
    }

    private void reconcile(User user) {
        if (!passwordEncoder.matches(DEMO_PASSWORD, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
            log.info("Demo admin password rehashed with Spring BCrypt (dev/test only)");
        }
    }
}
