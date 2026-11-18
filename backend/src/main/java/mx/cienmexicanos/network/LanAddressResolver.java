package mx.cienmexicanos.network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mx.cienmexicanos.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Detecta la IPv4 de la LAN para construir la URL del QR (plan D8, AC-04).
 * Prioriza 192.168.x.x, luego 10.x.x.x y por último 172.16–31.x.x (suelen ser VPN/Docker).
 */
@Component
public class LanAddressResolver {

    private final AppProperties properties;

    public LanAddressResolver(AppProperties properties) {
        this.properties = properties;
    }

    public String resolve() {
        if (properties.lanAddress() != null && !properties.lanAddress().isBlank()) {
            return properties.lanAddress().trim();
        }
        return detect().orElse("127.0.0.1");
    }

    Optional<String> detect() {
        try {
            return NetworkInterface.networkInterfaces()
                    .filter(LanAddressResolver::isUsable)
                    .flatMap(NetworkInterface::inetAddresses)
                    .filter(a -> a instanceof Inet4Address && a.isSiteLocalAddress())
                    .map(InetAddress::getHostAddress)
                    .min(Comparator.comparingInt(LanAddressResolver::rank));
        } catch (SocketException e) {
            return Optional.empty();
        }
    }

    static int rank(String ip) {
        List<String> preferred = List.of("192.168.", "10.", "172.");
        for (int i = 0; i < preferred.size(); i++) {
            if (ip.startsWith(preferred.get(i))) {
                return i;
            }
        }
        return preferred.size();
    }

    private static boolean isUsable(NetworkInterface nic) {
        try {
            return nic.isUp() && !nic.isLoopback() && !nic.isVirtual() && !nic.isPointToPoint();
        } catch (SocketException e) {
            return false;
        }
    }
}
