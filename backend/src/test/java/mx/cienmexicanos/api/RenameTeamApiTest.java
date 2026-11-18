package mx.cienmexicanos.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Spec 003, T801–T802: captainId en las proyecciones y PUT /api/rooms/{code}/teams/{team}/name (renameTeam). */
@SpringBootTest
@ActiveProfiles("test")
class RenameTeamApiTest {

    static final String PT = "X-Presenter-Token";
    static final String PLT = "X-Player-Token";
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    record Created(String code, String token) {}

    record Joined(String id, String token) {}

    JsonNode body(ResultActions r) throws Exception {
        return JSON.readTree(r.andReturn().getResponse().getContentAsString());
    }

    Created createRoom() throws Exception {
        JsonNode b = body(mvc.perform(post("/api/rooms")).andExpect(status().isCreated()));
        return new Created(b.get("code").asString(), b.get("presenterToken").asString());
    }

    Joined join(String code, String name, String team) throws Exception {
        JsonNode b = body(mvc.perform(post("/api/rooms/" + code + "/players").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"team\":\"" + team + "\"}")).andExpect(status().isCreated()));
        return new Joined(b.get("player").get("id").asString(), b.get("playerToken").asString());
    }

    ResultActions rename(String code, String team, String token, String name) throws Exception {
        var req = put("/api/rooms/" + code + "/teams/" + team + "/name").contentType(MediaType.APPLICATION_JSON)
                .content(name == null ? "{}" : "{\"name\":\"" + name + "\"}");
        if (token != null) {
            req.header(PLT, token);
        }
        return mvc.perform(req);
    }

    int version(String code) throws Exception {
        return body(mvc.perform(get("/api/rooms/" + code))).get("version").asInt();
    }

    @Test
    @DisplayName("AC-62 captainId siempre presente: null sin jugadores y luego el primer jugador, en ambas proyecciones")
    void captainIdInProjections() throws Exception {
        Created c = createRoom();
        mvc.perform(get("/api/rooms/" + c.code()))
                .andExpect(jsonPath("$.teams[0].captainId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.teams[0].captainId").hasJsonPath())
                .andExpect(jsonPath("$.teams[1].captainId").hasJsonPath());

        Joined ana = join(c.code(), "Ana", "A");
        join(c.code(), "Carla", "A");
        Joined beto = join(c.code(), "Beto", "B");
        mvc.perform(get("/api/rooms/" + c.code()))
                .andExpect(jsonPath("$.teams[0].captainId").value(ana.id()))
                .andExpect(jsonPath("$.teams[1].captainId").value(beto.id()));
        mvc.perform(get("/api/rooms/" + c.code() + "/presenter-view").header(PT, c.token()))
                .andExpect(jsonPath("$.teams[0].captainId").value(ana.id()))
                .andExpect(jsonPath("$.teams[1].captainId").value(beto.id()));
    }

    @Test
    @DisplayName("AC-63 AC-65 el capitán renombra (recortado): 200 con RoomState público y version + 1; mismo nombre: no-op")
    void captainRenames() throws Exception {
        Created c = createRoom();
        Joined ana = join(c.code(), "Ana", "A");
        int before = version(c.code());

        rename(c.code(), "A", ana.token(), "  Los Leones ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].name").value("Los Leones"))
                .andExpect(jsonPath("$.teams[0].captainId").value(ana.id()))
                .andExpect(jsonPath("$.version").value(before + 1))
                .andExpect(jsonPath("$.presenterBoard").doesNotExist());
        mvc.perform(get("/api/rooms/" + c.code())).andExpect(jsonPath("$.teams[0].name").value("Los Leones"));

        rename(c.code(), "A", ana.token(), "Los Leones")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(before + 1));
    }

    @Test
    @DisplayName("AC-64 sin token 401 UNAUTHORIZED; token inválido, de otra sala o no capitán → 403 NOT_CAPTAIN; 404")
    void authErrors() throws Exception {
        Created c = createRoom();
        Joined ana = join(c.code(), "Ana", "A");
        Joined carla = join(c.code(), "Carla", "A");
        Joined beto = join(c.code(), "Beto", "B");
        Created other = createRoom();
        Joined otherCaptain = join(other.code(), "Eva", "A");

        rename(c.code(), "A", null, "Leones").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        rename(c.code(), "A", "nope", "Leones").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CAPTAIN"));
        rename(c.code(), "A", otherCaptain.token(), "Leones").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CAPTAIN"));
        rename(c.code(), "A", carla.token(), "Leones").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CAPTAIN"));
        rename(c.code(), "A", beto.token(), "Leones").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CAPTAIN"));
        rename(c.code(), "A", c.token(), "Leones").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CAPTAIN"));
        rename("ZZZZ", "A", ana.token(), "Leones").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        rename(c.code(), "C", ana.token(), "Leones").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("AC-64 longitud 2–20 tras recortar → 400 VALIDATION_ERROR; nombre del otro equipo → 409 TEAM_NAME_TAKEN")
    void validationAndTaken() throws Exception {
        Created c = createRoom();
        Joined ana = join(c.code(), "Ana", "A");
        int before = version(c.code());

        rename(c.code(), "A", ana.token(), " L ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        rename(c.code(), "A", ana.token(), "x".repeat(21)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        rename(c.code(), "A", ana.token(), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        rename(c.code(), "A", ana.token(), "equipo verde").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEAM_NAME_TAKEN"));
        rename(c.code(), "A", ana.token(), "Ok").andExpect(status().isOk());
        rename(c.code(), "A", ana.token(), "  " + "x".repeat(20) + "  ").andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(before + 2));
    }

    @Test
    @DisplayName("AC-64 fuera de LOBBY → 409 ROOM_NOT_IN_LOBBY")
    void notInLobby() throws Exception {
        Created c = createRoom();
        Joined ana = join(c.code(), "Ana", "A");
        join(c.code(), "Beto", "B");
        mvc.perform(post("/api/rooms/" + c.code() + "/start").header(PT, c.token())).andExpect(status().isOk());
        rename(c.code(), "A", ana.token(), "Leones").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_IN_LOBBY"));
    }
}
