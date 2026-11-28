package ch.barbulescu.testability.examples.plaincontexts;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GermanConfig {

    /** Slow on purpose, so the report's contextLoadMs has a known lower bound. */
    @Bean
    Greeter greeter() throws InterruptedException {
        Thread.sleep(500);
        return new Greeter("Hallo");
    }
}
