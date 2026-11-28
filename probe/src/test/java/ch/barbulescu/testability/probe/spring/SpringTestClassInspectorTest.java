package ch.barbulescu.testability.probe.spring;

import org.junit.runner.RunWith;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @DirtiesContext
    static class DirtiesClassFixture {
        @DirtiesContext
        void dirtiesAfterThisMethod() {
        }

        void leavesTheContext() {
        }
    }

    static class InheritsDirtiesContextFixture extends DirtiesClassFixture {
    }

    @org.junit.jupiter.api.Test
    void detectsDirtiesContextOnClassAndMethods() {
        SpringTestClassFacts facts = SpringTestClassInspector.inspect(DirtiesClassFixture.class);

        assertTrue(facts.dirtiesContextClass);
        assertEquals(1, facts.dirtiesContextMethods);
    }

    @org.junit.jupiter.api.Test
    void detectsDirtiesContextInheritedFromBaseClass() {
        SpringTestClassFacts facts = SpringTestClassInspector.inspect(InheritsDirtiesContextFixture.class);

        assertTrue(facts.dirtiesContextClass);
        assertEquals(1, facts.dirtiesContextMethods);
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
        assertFalse(facts.dirtiesContextClass);
        assertEquals(0, facts.dirtiesContextMethods);
    }

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    static class RealHttpFixture {
    }

    @org.junit.jupiter.api.Test
    void readsTheWebEnvironmentOfABootTest() {
        assertEquals("RANDOM_PORT", SpringTestClassInspector.inspect(RealHttpFixture.class).webEnvironment);
        assertEquals("MOCK", SpringTestClassInspector.inspect(MockEverythingFixture.class).webEnvironment);
        assertNull(SpringTestClassInspector.inspect(SliceTestFixture.class).webEnvironment);
    }

    @JdbcTest
    static class EmbeddedDatabaseSliceFixture {
    }

    @JdbcTest
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    static class RealDatabaseSliceFixture {
    }

    @SpringBootTest
    @AutoConfigureTestDatabase
    static class ReplacedDatabaseBootFixture {
    }

    @org.junit.jupiter.api.Test
    void detectsATestDatabaseReplacedDirectlyOrThroughASlice() {
        assertTrue(SpringTestClassInspector.inspect(EmbeddedDatabaseSliceFixture.class).testDatabaseReplaced);
        assertTrue(SpringTestClassInspector.inspect(ReplacedDatabaseBootFixture.class).testDatabaseReplaced);
    }

    @org.junit.jupiter.api.Test
    void replaceNoneOnTheClassWinsOverTheSlicesDefault() {
        assertFalse(SpringTestClassInspector.inspect(RealDatabaseSliceFixture.class).testDatabaseReplaced);
    }

    static class ContainerBaseFixture {
        @ServiceConnection
        static org.testcontainers.containers.GenericContainer<?> database;

        @DynamicPropertySource
        static void containerProperties(DynamicPropertyRegistry registry) {
        }
    }

    @SpringBootTest
    static class InheritsContainerWiringFixture extends ContainerBaseFixture {
    }

    @org.junit.jupiter.api.Test
    void detectsContainerWiringInheritedFromBaseClass() {
        SpringTestClassFacts facts = SpringTestClassInspector.inspect(InheritsContainerWiringFixture.class);

        assertTrue(facts.serviceConnection);
        assertTrue(facts.dynamicPropertySource);
    }

    @org.junit.jupiter.api.Test
    void plainClassHasNoWiring() {
        SpringTestClassFacts facts = SpringTestClassInspector.inspect(PlainJUnit5Fixture.class);

        assertNull(facts.webEnvironment);
        assertFalse(facts.testDatabaseReplaced);
        assertFalse(facts.serviceConnection);
        assertFalse(facts.dynamicPropertySource);
        assertFalse(facts.embeddedKafka);
    }
}
