package ch.barbulescu.testability.examples.wiring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A real server on a random port, reached over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealHttpTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void greetsOverHttp() {
        assertEquals("hello", rest.getForObject("/greeting", String.class));
    }
}
