package mx.cienmexicanos.network;

import mx.cienmexicanos.config.AppProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** operationId: getNetworkInfo (T006). */
@RestController
@RequestMapping("/api/network-info")
public class NetworkInfoController {

    private final LanAddressResolver resolver;
    private final AppProperties properties;
    private final Environment environment;

    public NetworkInfoController(LanAddressResolver resolver, AppProperties properties, Environment environment) {
        this.resolver = resolver;
        this.properties = properties;
        this.environment = environment;
    }

    @GetMapping
    public NetworkInfo get() {
        return new NetworkInfo(resolver.resolve(), backendPort(), properties.frontendPort());
    }

    /** Puerto real del servidor (también con {@code server.port=0}); se resuelve en cada petición. */
    private int backendPort() {
        Integer local = environment.getProperty("local.server.port", Integer.class);
        if (local != null && local > 0) {
            return local;
        }
        Integer configured = environment.getProperty("server.port", Integer.class, 8080);
        return configured > 0 ? configured : 8080;
    }
}
