package mx.cienmexicanos.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * T1401 (spec 009, AC-124): las rutas del cliente React devuelven {@code index.html} al abrirse
 * directamente (QR o recarga). Debe coincidir con {@code frontend/src/app/routes.tsx}.
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/screen/{code}", "/host/{code}", "/join/{code}", "/play/{code}", "/gracias"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
