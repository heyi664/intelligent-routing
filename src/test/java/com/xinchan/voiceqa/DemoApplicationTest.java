package com.xinchan.voiceqa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DemoApplicationTest {

    @Test
    void applicationStartupDoesNotRunHardcodedDemoScenarios() {
        assertFalse(
            CommandLineRunner.class.isAssignableFrom(DemoApplication.class),
            "DemoApplication should only start the Spring Boot service, not print hardcoded demo scenarios"
        );
    }
}