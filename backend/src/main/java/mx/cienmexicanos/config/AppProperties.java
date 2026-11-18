package mx.cienmexicanos.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades {@code app.*} de application.yml.
 *
 * @param lanAddress           IP que se usa en el QR; vacía = detección automática (AC-04)
 * @param frontendPort         puerto del cliente web en la LAN
 * @param allowedOriginPatterns orígenes permitidos para REST y WebSocket
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String lanAddress, int frontendPort, List<String> allowedOriginPatterns) {

    public AppProperties {
        allowedOriginPatterns = allowedOriginPatterns == null ? List.of() : List.copyOf(allowedOriginPatterns);
    }
}
