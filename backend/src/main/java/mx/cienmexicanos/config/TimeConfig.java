package mx.cienmexicanos.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Reloj inyectable y tareas programadas (expiración de salas, AC-28). */
@Configuration
@EnableScheduling
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
