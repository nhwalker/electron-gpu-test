package com.github.nhwalker.electrongputest;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docker-free sanity checks for the test harness itself: confirms the Java 25
 * toolchain is in effect and that every requested library is on the classpath.
 * These always run, so CI fails loudly if the project wiring regresses.
 */
@Epic("electron-gpu-test")
@Feature("Test harness")
class StackSmokeTest {

    @Test
    @DisplayName("Runs on a Java 25 toolchain")
    @Description("Sanity check that the configured Java 25 toolchain is the JVM executing the tests.")
    void runsOnJava25() {
        int major = Runtime.version().feature();
        assertEquals(25, major, "Expected the tests to run on Java 25 but got " + Runtime.version());
    }

    @Test
    @DisplayName("Core test libraries are on the classpath")
    @Description("Verifies Testcontainers, Selenium, Allure and JUnit Jupiter all resolve.")
    void coreLibrariesPresent() {
        assertTrue(libraryPresent("org.testcontainers.containers.GenericContainer"), "Testcontainers missing");
        assertTrue(libraryPresent("org.openqa.selenium.WebDriver"), "Selenium missing");
        assertTrue(libraryPresent("io.qameta.allure.Allure"), "Allure missing");
        assertTrue(libraryPresent("org.junit.jupiter.api.Test"), "JUnit Jupiter missing");
    }

    @Step("Resolve class {className}")
    private static boolean libraryPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
