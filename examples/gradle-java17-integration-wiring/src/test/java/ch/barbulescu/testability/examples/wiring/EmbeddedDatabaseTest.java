package ch.barbulescu.testability.examples.wiring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code @JdbcTest} replaces the configured database with an embedded one by default. */
@JdbcTest
class EmbeddedDatabaseTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void queriesTheEmbeddedDatabase() {
        assertEquals(1, jdbc.queryForObject("select 1", Integer.class));
    }
}
