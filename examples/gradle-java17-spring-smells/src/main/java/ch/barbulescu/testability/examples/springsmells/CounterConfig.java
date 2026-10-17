package ch.barbulescu.testability.examples.springsmells;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CounterConfig {

    @Bean
    Counter counter() {
        return new Counter();
    }
}
