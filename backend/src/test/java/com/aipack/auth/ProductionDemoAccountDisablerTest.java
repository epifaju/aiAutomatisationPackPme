package com.aipack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aipack.identity.User;
import com.aipack.identity.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class ProductionDemoAccountDisablerTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void disablesDemoAccountInProduction() {
        User demo = new User();
        demo.setEmail(DemoAdminPasswordReconciler.DEMO_EMAIL);
        demo.setEnabled(true);
        when(userRepository.findAllByEmailIgnoreCase(DemoAdminPasswordReconciler.DEMO_EMAIL))
                .thenReturn(List.of(demo));

        new ProductionDemoAccountDisabler(userRepository, "production", "").run(new DefaultApplicationArguments());

        assertThat(demo.isEnabled()).isFalse();
    }

    @Test
    void skipsWhenDevelopment() {
        new ProductionDemoAccountDisabler(userRepository, "development", "").run(new DefaultApplicationArguments());
        verifyNoInteractions(userRepository);
    }

    @Test
    void disablesWhenDemoSeedExplicitlyOff() {
        User demo = new User();
        demo.setEmail(DemoAdminPasswordReconciler.DEMO_EMAIL);
        demo.setEnabled(true);
        when(userRepository.findAllByEmailIgnoreCase(DemoAdminPasswordReconciler.DEMO_EMAIL))
                .thenReturn(List.of(demo));

        new ProductionDemoAccountDisabler(userRepository, "development", "false")
                .run(new DefaultApplicationArguments());

        assertThat(demo.isEnabled()).isFalse();
    }
}
