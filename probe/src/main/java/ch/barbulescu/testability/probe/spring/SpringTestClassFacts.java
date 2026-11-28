package ch.barbulescu.testability.probe.spring;

final class SpringTestClassFacts {

    final boolean junit4;
    final boolean bootTest;
    final boolean sliceTest;
    final int mockBeanFields;
    final int spyBeanFields;
    final boolean dirtiesContextClass;
    final int dirtiesContextMethods;
    /** {@code @SpringBootTest}'s {@code webEnvironment} (MOCK, RANDOM_PORT, ...); null without one. */
    final String webEnvironment;
    final boolean testDatabaseReplaced;
    final boolean serviceConnection;
    final boolean dynamicPropertySource;
    final boolean embeddedKafka;

    SpringTestClassFacts(boolean junit4, boolean bootTest, boolean sliceTest, int mockBeanFields, int spyBeanFields,
            boolean dirtiesContextClass, int dirtiesContextMethods, String webEnvironment,
            boolean testDatabaseReplaced, boolean serviceConnection, boolean dynamicPropertySource,
            boolean embeddedKafka) {
        this.junit4 = junit4;
        this.bootTest = bootTest;
        this.sliceTest = sliceTest;
        this.mockBeanFields = mockBeanFields;
        this.spyBeanFields = spyBeanFields;
        this.dirtiesContextClass = dirtiesContextClass;
        this.dirtiesContextMethods = dirtiesContextMethods;
        this.webEnvironment = webEnvironment;
        this.testDatabaseReplaced = testDatabaseReplaced;
        this.serviceConnection = serviceConnection;
        this.dynamicPropertySource = dynamicPropertySource;
        this.embeddedKafka = embeddedKafka;
    }
}
