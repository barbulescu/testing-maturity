package ch.barbulescu.testability.examples.plaincontexts;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnglishConfig {

    @Bean
    Greeter greeter() {
        return new Greeter("Hello");
    }
}
