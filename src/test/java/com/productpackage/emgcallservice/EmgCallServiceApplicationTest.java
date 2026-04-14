package com.productpackage.emgcallservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.times;

/**
 * Tests for EmgCallServiceApplication to cover main entry without starting Spring.
 *
 * Note:
 * - These tests mock SpringApplication.run(...) to prevent context startup.
 * - If your project cannot mock static methods, add mockito-inline as a test dependency.
 */
@DisplayName("EmgCallServiceApplication 单元测试")
class EmgCallServiceApplicationTest {

    @Test
    @DisplayName("testMain_WithArgs_CallsSpringRun")
    void testMain_WithArgs_CallsSpringRun() {
        // Use the same array instance for when(...) and the actual call to ensure exact match.
        final String[] args = new String[] {"one", "two"};
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            // stub the exact invocation with the same args instance
            mocked.when(() -> SpringApplication.run(EmgCallServiceApplication.class, args)).thenReturn(null);

            // call main with the same array instance
            EmgCallServiceApplication.main(args);

            // verify the static method was invoked once with the same args instance
            mocked.verify(() -> SpringApplication.run(EmgCallServiceApplication.class, args), times(1));
        }
    }

    @Test
    @DisplayName("testMain_NullArgs_CallsSpringRun")
    void testMain_NullArgs_CallsSpringRun() {
        final String[] args = null;
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            // stub the invocation with null args
            mocked.when(() -> SpringApplication.run(EmgCallServiceApplication.class, args)).thenReturn(null);

            // call main with null
            EmgCallServiceApplication.main(args);

            // verify the static method was invoked once with null args
            mocked.verify(() -> SpringApplication.run(EmgCallServiceApplication.class, args), times(1));
        }
    }
}
