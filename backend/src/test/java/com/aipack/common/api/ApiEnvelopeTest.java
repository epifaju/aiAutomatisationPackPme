package com.aipack.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ApiEnvelopeTest {

    @Test
    void okResponseWrapsData() {
        ApiResponse<String> response = ApiResponse.ok("pong");
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("pong");
        assertThat(response.error()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void pageResponseMapsSpringPage() {
        PageResponse<String> page = PageResponse.from(
                new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5));
        assertThat(page.content()).containsExactly("a", "b");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }
}
