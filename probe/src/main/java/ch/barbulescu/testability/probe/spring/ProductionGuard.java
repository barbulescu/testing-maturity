package ch.barbulescu.testability.probe.spring;

/**
 * The {@code ApplicationListener} attaches to every {@code SpringApplication}
 * start, including a real production boot - it must no-op there. Presence
 * of {@code spring-test} on the classpath is what distinguishes a test JVM
 * from a production one; if this jar ever leaked onto a runtime classpath
 * without spring-test, this guard keeps it inert.
 */
final class ProductionGuard {

    private static final boolean TEST_JVM = isSpringTestOnClasspath();

    private ProductionGuard() {
    }

    static boolean isTestJvm() {
        return TEST_JVM;
    }

    private static boolean isSpringTestOnClasspath() {
        try {
            Class.forName("org.springframework.test.context.TestContext", false, ProductionGuard.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
