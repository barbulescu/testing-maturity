package ch.barbulescu.testability.probe.spring;

final class SpringTestClassFacts {

    final boolean junit4;
    final boolean bootTest;
    final boolean sliceTest;
    final int mockBeanFields;
    final int spyBeanFields;
    final boolean dirtiesContextClass;
    final int dirtiesContextMethods;

    SpringTestClassFacts(boolean junit4, boolean bootTest, boolean sliceTest, int mockBeanFields, int spyBeanFields,
            boolean dirtiesContextClass, int dirtiesContextMethods) {
        this.junit4 = junit4;
        this.bootTest = bootTest;
        this.sliceTest = sliceTest;
        this.mockBeanFields = mockBeanFields;
        this.spyBeanFields = spyBeanFields;
        this.dirtiesContextClass = dirtiesContextClass;
        this.dirtiesContextMethods = dirtiesContextMethods;
    }
}
