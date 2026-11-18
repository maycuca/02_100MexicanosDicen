package mx.cienmexicanos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CienMexicanosApplication {

    public static void main(String[] args) {
        SpringApplication.run(CienMexicanosApplication.class, args);
    }
}
