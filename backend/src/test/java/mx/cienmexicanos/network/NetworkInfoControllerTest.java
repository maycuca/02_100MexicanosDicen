package mx.cienmexicanos.network;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "app.lan-address=192.168.1.42")
@ActiveProfiles("test")
class NetworkInfoControllerTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void returnsConfiguredLanAddress_AC04() throws Exception {
        mvc.perform(get("/api/network-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lanAddress").value("192.168.1.42"))
                .andExpect(jsonPath("$.frontendPort").value(5173))
                .andExpect(jsonPath("$.backendPort").isNumber());
    }
}
