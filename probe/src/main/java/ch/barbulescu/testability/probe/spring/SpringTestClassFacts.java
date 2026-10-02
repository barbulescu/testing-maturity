package ch.barbulescu.testability.probe.spring;

final class SpringTestClassFacts {

    final boolean junit4;
    final boolean bootTest;
    final boolean sliceTest;
    final int mockBeanFields;
    final int spyBeanFields;

    SpringTestClassFacts(boolean junit4, boolean bootTest, boolean sliceTest, int mockBeanFields, int spyBeanFields) {
        this.junit4 = junit4;
        this.bootTest = bootTest;
        this.sliceTest = sliceTest;
        this.mockBeanFields = mockBeanFields;
        this.spyBeanFields = spyBeanFields;
    }
}
