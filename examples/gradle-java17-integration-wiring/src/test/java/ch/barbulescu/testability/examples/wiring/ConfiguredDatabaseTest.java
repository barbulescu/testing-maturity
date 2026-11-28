package ch.barbulescu.testability.examples.wiring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Keeps the configured database, whose URL comes from {@code @DynamicPropertySource} the
 * way a container's would. H2 stands in for the container, so no Docker is needed.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ConfiguredDatabaseTest {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:configured");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void queriesTheConfiguredDatabase() {
        assertEquals(1, jdbc.queryForObject("select 1", Integer.class));
    }
}
