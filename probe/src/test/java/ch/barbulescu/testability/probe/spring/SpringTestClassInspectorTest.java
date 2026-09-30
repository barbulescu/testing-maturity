package ch.barbulescu.testability.probe.spring;

import org.junit.runner.RunWith;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringTestClassInspectorTest {

    @RunWith(SpringRunner.class)
    @SpringBootTest
    static class ClassicSpringRunnerFixture {
    }

    @org.junit.jupiter.api.Test
    void detectsJUnit4RunnerAndBootTest() {
        SpringTestClassFacts facts = SpringTestClassInspector.inspect(ClassicSpringRunnerFixture.class);

        assertTrue(facts.junit4);
        assertTrue(facts.bootTest);
        assertFalse(facts.sliceTest);
        assertEquals(0, facts.mockBeanFields);
    }

    @WebMvcTest
    static class SliceTestFixture {
    }

    @org.junit.jupiter.api.Test
    void detectsSliceTestAndNotBootTest() {
        SpringTestClassFacts facts = SpringTestClassInspector.inspect(SliceTestFixture.class);

        assertFalse(facts.junit4);
        assertFalse(facts.bootTest);
        assertTrue(facts.sliceTest);
    }

    @SpringBootTest
    static class MockEverythingFixture {
        @MockBean
        private Object legacyMock;

        @MockitoBean
        private Object currentMock;

        private Object notMocked;
    }

    @org.junit.jupiter.api.Test
    void countsBothMockBeanAndMockitoBeanFields() {
        SpringTestClassFacts facts = SpringTestClassInspector.inspect(MockEverythingFixture.class);

        assertEquals(2, facts.mockBeanFields);
    }

    static class PlainBaseFixture {
        @MockBean
        private Object inheritedMock;
    }

    @SpringBootTest
    static class InheritingFixture extends PlainBaseFixture {
    }

    @org.junit.jupiter.api.Test
    void countsMockBeanFieldsFromSuperclasses() {
        SpringTestClassFacts facts = SpringTestClassInspector.inspect(InheritingFixture.class);

        assertEquals(1, facts.mockBeanFields);
    }

    static class PlainJUnit5Fixture {
    }

    @org.junit.jupiter.api.Test
    void plainClassHasNoFacts() {
        SpringTestClassFacts facts = SpringTestClassInspector.inspect(PlainJUnit5Fixture.class);

        assertFalse(facts.junit4);
        assertFalse(facts.bootTest);
        assertFalse(facts.sliceTest);
        assertEquals(0, facts.mockBeanFields);
    }
}
