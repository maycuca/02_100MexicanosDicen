package mx.cienmexicanos.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** T1421 (spec 009): fallback SPA (AC-124) y QR con IP pública en el puerto 80 (AC-125). */
@SpringBootTest(properties = {"app.lan-address=203.0.113.10", "app.frontend-port=80"})
@ActiveProfiles("test")
class SpaForwardControllerTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/screen/ABCD", "/host/ABCD", "/join/ABCD", "/play/ABCD", "/gracias"})
    void clientRoutesForwardToIndex_AC124(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void apiIsNotForwarded_AC124() throws Exception {
        mvc.perform(get("/api/network-info"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(null));
    }

    @Test
    void networkInfoUsesPublicAddressAndPort80_AC125() throws Exception {
        mvc.perform(get("/api/network-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lanAddress").value("203.0.113.10"))
                .andExpect(jsonPath("$.frontendPort").value(80));
    }
}
