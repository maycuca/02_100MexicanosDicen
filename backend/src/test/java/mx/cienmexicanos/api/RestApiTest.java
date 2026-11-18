package mx.cienmexicanos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import mx.cienmexicanos.game.GameResultRepository;
import mx.cienmexicanos.game.TurnTimerScheduler;
import mx.cienmexicanos.room.RoomExpiryJob;
import mx.cienmexicanos.room.RoomRegistry;
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

/** Pruebas MockMvc de los endpoints de openapi.yaml (T201–T205b). */
@SpringBootTest
@ActiveProfiles("test")
class RestApiTest {

    static final String PT = "X-Presenter-Token";
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired WebApplicationContext context;
    @Autowired GameResultRepository results;
    @Autowired RoomRegistry registry;
    @Autowired TurnTimerScheduler timers;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    record Created(String code, String token, String claim) {}

    JsonNode body(ResultActions r) throws Exception {
        return JSON.readTree(r.andReturn().getResponse().getContentAsString());
    }

    Created createRoom(String json) throws Exception {
        var req = post("/api/rooms");
        if (json != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        JsonNode b = body(mvc.perform(req).andExpect(status().isCreated()));
        return new Created(b.get("code").asString(), b.get("presenterToken").asString(),
                b.get("presenterClaimToken").asString());
    }

    ResultActions join(String code, String name, String team) throws Exception {
        return mvc.perform(post("/api/rooms/" + code + "/players").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"team\":\"" + team + "\"}"));
    }

    ResultActions presenterPost(Created room, String path) throws Exception {
        return mvc.perform(post("/api/rooms/" + room.code() + path).header(PT, room.token()));
    }

    @Test
    @DisplayName("AC-01 AC-02 crear sala con defaults: código válido, tokens y estado LOBBY")
    void createDefaults() throws Exception {
        mvc.perform(post("/api/rooms"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("^[A-HJ-NP-Z]{4}$")))
                .andExpect(jsonPath("$.presenterToken").isString())
                .andExpect(jsonPath("$.presenterClaimToken").isString())
                .andExpect(jsonPath("$.state.status").value("LOBBY"))
                .andExpect(jsonPath("$.state.totalRounds").value(5))
                .andExpect(jsonPath("$.state.targetScore").value(300))
                .andExpect(jsonPath("$.state.presenterConnected").value(false))
                .andExpect(jsonPath("$.state.teams[0].name").value("Equipo Rojo"))
                .andExpect(jsonPath("$.state.teams[1].name").value("Equipo Verde"))
                .andExpect(jsonPath("$.state.version").isNumber())
                .andExpect(jsonPath("$.state.presenterBoard").isArray());
    }

    @Test
    @DisplayName("AC-02 configuración personalizada y validación → 400 con code")
    void createCustom() throws Exception {
        Created c = createRoom("{\"teamAName\":\"Leones\",\"teamBName\":\"Tigres\",\"rounds\":3,\"targetScore\":50}");
        mvc.perform(get("/api/rooms/" + c.code()))
                .andExpect(jsonPath("$.teams[0].name").value("Leones"))
                .andExpect(jsonPath("$.totalRounds").value(3))
                .andExpect(jsonPath("$.targetScore").value(50));
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content("{\"rounds\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("sala inexistente → 404 ROOM_NOT_FOUND")
    void notFound() throws Exception {
        mvc.perform(get("/api/rooms/ZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        mvc.perform(post("/api/rooms/ZZZZ/start").header(PT, "x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("AC-26 acciones de presentador: sin token 401, token ajeno 403")
    void presenterAuth() throws Exception {
        Created c = createRoom(null);
        Created other = createRoom(null);
        for (String path : new String[] {"/start", "/rounds/current/strike", "/rounds/current/answers/1/reveal",
                "/next-round", "/rematch", "/presenter/claim-token"}) {
            mvc.perform(post("/api/rooms/" + c.code() + path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            mvc.perform(post("/api/rooms/" + c.code() + path).header(PT, other.token()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        mvc.perform(get("/api/rooms/" + c.code() + "/presenter-view")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rooms/" + c.code() + "/presenter-view").header(PT, other.token()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/rooms/" + c.code() + "/rounds/current/control").contentType(MediaType.APPLICATION_JSON)
                .content("{\"team\":\"A\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC-30 AC-32 AC-33 canje único del QR de presentador y regeneración")
    void presenterClaim() throws Exception {
        Created c = createRoom(null);
        String url = "/api/rooms/" + c.code() + "/presenter/claim";

        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"claimToken\":\"nope\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CLAIM_INVALID"));

        JsonNode claimed = body(mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimToken\":\"" + c.claim() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.presenterConnected").value(true)));
        String phone = claimed.get("presenterToken").asString();
        assertThat(phone).isNotEqualTo(c.token());

        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"claimToken\":\"" + c.claim() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_USED"));
        mvc.perform(get("/api/rooms/" + c.code() + "/presenter-view").header(PT, phone)).andExpect(status().isOk());
        mvc.perform(get("/api/rooms/" + c.code())).andExpect(jsonPath("$.presenterConnected").value(true));

        JsonNode regenerated = body(mvc.perform(post("/api/rooms/" + c.code() + "/presenter/claim-token")
                .header(PT, c.token())).andExpect(status().isOk()));
        String newClaim = regenerated.get("claimToken").asString();
        assertThat(newClaim).isNotEqualTo(c.claim());

        mvc.perform(get("/api/rooms/" + c.code())).andExpect(jsonPath("$.presenterConnected").value(false));
        mvc.perform(get("/api/rooms/" + c.code() + "/presenter-view").header(PT, phone))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/rooms/" + c.code() + "/presenter-view").header(PT, c.token()))
                .andExpect(status().isOk());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"claimToken\":\"" + newClaim + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/rooms/ZZZZ/presenter/claim").contentType(MediaType.APPLICATION_JSON)
                .content("{\"claimToken\":\"x\"}")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-05 AC-06 AC-07 AC-09 unirse, nombre repetido, fuera de lobby y recuperar sesión")
    void players() throws Exception {
        Created c = createRoom(null);
        JsonNode joined = body(join(c.code(), "Ana", "A")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.player.name").value("Ana"))
                .andExpect(jsonPath("$.player.team").value("A"))
                .andExpect(jsonPath("$.player.connected").isBoolean())
                .andExpect(jsonPath("$.playerToken").isString()));
        String token = joined.get("playerToken").asString();

        join(c.code(), "aNA", "B").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NAME_TAKEN"));
        join(c.code(), "A", "B").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        join(c.code(), "Beto", "C").andExpect(status().isBadRequest());
        join("ZZZZ", "Beto", "B").andExpect(status().isNotFound());

        mvc.perform(get("/api/rooms/" + c.code() + "/players/me").header("X-Player-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(joined.get("player").get("id").asString()))
                .andExpect(jsonPath("$.name").value("Ana"));
        mvc.perform(get("/api/rooms/" + c.code() + "/players/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rooms/" + c.code() + "/players/me").header("X-Player-Token", "nope"))
                .andExpect(status().isUnauthorized());

        presenterPost(c, "/start").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEAM_EMPTY"));
        join(c.code(), "Beto", "B").andExpect(status().isCreated());
        presenterPost(c, "/start").andExpect(status().isOk());
        join(c.code(), "Carla", "A").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_IN_LOBBY"));
    }

    @Test
    @DisplayName("AC-11 … AC-23 partida completa por REST; AC-25 la vista pública no filtra respuestas; game_result")
    void fullGame() throws Exception {
        Created c = createRoom("{\"rounds\":3,\"targetScore\":1000}");
        join(c.code(), "Ana", "A");
        join(c.code(), "Beto", "B");
        long resultsBefore = results.findByRoomCode(c.code()).size();

        JsonNode state = body(presenterPost(c, "/start")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FACE_OFF"))
                .andExpect(jsonPath("$.round.number").value(1))
                .andExpect(jsonPath("$.round.multiplier").value(1))
                .andExpect(jsonPath("$.round.faceOff.playerA").isString())
                .andExpect(jsonPath("$.round.potWinner").doesNotExist()));
        presenterPost(c, "/start").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));

        for (int round = 1; round <= 3; round++) {
            JsonNode board = state.get("presenterBoard");
            long top = board.get(0).get("answerId").asLong();
            String topText = board.get(0).get("text").asString();

            // AC-25: la vista pública no contiene el texto de la respuesta oculta
            String publicJson = mvc.perform(get("/api/rooms/" + c.code())).andReturn().getResponse().getContentAsString();
            assertThat(publicJson).doesNotContain(topText).doesNotContain("answerId");

            // spec 006 AC-100: en el cara a cara no se revela sin buzzer (el buzz es por STOMP; aquí se corrige el control)
            presenterPost(c, "/rounds/current/answers/" + top + "/reveal").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NO_ACTIVE_TEAM"));
            mvc.perform(post("/api/rooms/" + c.code() + "/rounds/current/control").header(PT, c.token())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
            mvc.perform(post("/api/rooms/" + c.code() + "/rounds/current/control").header(PT, c.token())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"team\":\"A\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PLAYING"));
            presenterPost(c, "/rounds/current/answers/" + top + "/reveal").andExpect(status().isOk())
                    .andExpect(jsonPath("$.round.pot").value(board.get(0).get("points").asInt()));
            presenterPost(c, "/rounds/current/answers/999999/reveal").andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ANSWER_NOT_FOUND"));
            presenterPost(c, "/rounds/current/strike");
            presenterPost(c, "/rounds/current/strike");
            presenterPost(c, "/rounds/current/strike").andExpect(jsonPath("$.status").value("STEAL"));
            presenterPost(c, "/rounds/current/strike")
                    .andExpect(jsonPath("$.status").value("ROUND_END"))
                    .andExpect(jsonPath("$.round.potWinner").value("A"))
                    .andExpect(jsonPath("$.round.board[*].revealed").value(org.hamcrest.Matchers.everyItem(
                            org.hamcrest.Matchers.is(true))));
            state = body(presenterPost(c, "/next-round").andExpect(status().isOk()));
        }
        assertThat(state.get("status").asString()).isEqualTo("GAME_OVER");
        assertThat(state.get("winner").asString()).isEqualTo("A");
        assertThat(results.findByRoomCode(c.code())).hasSize((int) resultsBefore + 1)
                .last().satisfies(r -> {
                    assertThat(r.getWinner()).isEqualTo("A");
                    assertThat(r.getTeamAName()).isEqualTo("Equipo Rojo");
                    assertThat(r.getTeamBScore()).isZero();
                    assertThat(r.getFinishedAt()).isNotBlank();
                });

        presenterPost(c, "/rematch").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOBBY"))
                .andExpect(jsonPath("$.round").isEmpty())
                .andExpect(jsonPath("$.winner").isEmpty())
                .andExpect(jsonPath("$.teams[0].score").value(0))
                .andExpect(jsonPath("$.teams[0].players[0].name").value("Ana"));
    }

    @Test
    @DisplayName("AC-25 el JSON público omite text/points de casillas ocultas")
    void hiddenSlotsOmitFields() throws Exception {
        Created c = createRoom(null);
        join(c.code(), "Ana", "A");
        join(c.code(), "Beto", "B");
        presenterPost(c, "/start");
        mvc.perform(get("/api/rooms/" + c.code()))
                .andExpect(jsonPath("$.round.board[0].revealed").value(false))
                .andExpect(jsonPath("$.round.board[0].text").doesNotExist())
                .andExpect(jsonPath("$.round.board[0].points").doesNotExist())
                .andExpect(jsonPath("$.presenterBoard").doesNotExist());
    }

    @Test
    @DisplayName("T205 banco de preguntas solo para presentador")
    void questions() throws Exception {
        Created c = createRoom(null);
        mvc.perform(get("/api/questions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/questions").header(PT, "nope")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/questions").header(PT, c.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$[0].answers[0].position").value(1))
                .andExpect(jsonPath("$[0].answers[0].id").isNumber());
    }

    @Test
    @DisplayName("AC-28 el job de expiración cierra salas inactivas")
    void expiryJob() throws Exception {
        Created c = createRoom(null);
        RoomExpiryJob job = new RoomExpiryJob(registry,
                java.time.Clock.offset(java.time.Clock.systemUTC(), Duration.ofHours(3)), Duration.ofHours(2), timers);
        job.evictInactiveRooms();
        mvc.perform(get("/api/rooms/" + c.code())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("spec 002 AC-46 defaults del temporizador y del sonido en el estado de la sala")
    void timerDefaults() throws Exception {
        mvc.perform(post("/api/rooms"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state.turnSeconds").value(20))
                .andExpect(jsonPath("$.state.autoStrikeOnTimeout").value(true))
                .andExpect(jsonPath("$.state.soundEnabled").value(true));
        Created c = createRoom("{\"turnSeconds\":0,\"autoStrikeOnTimeout\":false}");
        mvc.perform(get("/api/rooms/" + c.code()))
                .andExpect(jsonPath("$.turnSeconds").value(0))
                .andExpect(jsonPath("$.autoStrikeOnTimeout").value(false))
                .andExpect(jsonPath("$.soundEnabled").value(true));
        createRoom("{\"turnSeconds\":10}");
        createRoom("{\"turnSeconds\":60}");
    }

    @Test
    @DisplayName("spec 002 AC-46 turnSeconds 1–9, negativo o > 60 → 400 VALIDATION_ERROR")
    void timerValidation() throws Exception {
        for (String value : new String[] {"1", "9", "-1", "61"}) {
            mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"turnSeconds\":" + value + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    private org.springframework.test.web.servlet.ResultActions timer(Created c, String action) throws Exception {
        return mvc.perform(post("/api/rooms/" + c.code() + "/rounds/current/timer").header(PT, c.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\"}"));
    }

    @Test
    @DisplayName("spec 005 AC-80 AC-81 temporizador: START/PAUSE/RESET y errores 401, 403, 409, 400")
    void timerEndpoint() throws Exception {
        Created c = createRoom("{\"turnSeconds\":20}");
        join(c.code(), "Ana", "A");
        join(c.code(), "Beto", "B");
        timer(c, "START").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        presenterPost(c, "/start").andExpect(status().isOk());

        mvc.perform(post("/api/rooms/" + c.code() + "/rounds/current/timer")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"START\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/rooms/" + c.code() + "/rounds/current/timer").header(PT, "nope")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"START\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        timer(c, "JUMP").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/rooms/" + c.code() + "/rounds/current/timer").header(PT, c.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        timer(c, "START").andExpect(status().isOk()).andExpect(jsonPath("$.round.timerRunning").value(true));
        timer(c, "PAUSE").andExpect(status().isOk()).andExpect(jsonPath("$.round.timerRunning").value(false))
                .andExpect(jsonPath("$.round.turnDeadline").value(org.hamcrest.Matchers.nullValue()));
        timer(c, "RESET").andExpect(status().isOk()).andExpect(jsonPath("$.round.timerRemainingMs").value(20_000));

        Created noTimer = createRoom("{\"turnSeconds\":0}");
        join(noTimer.code(), "Ana", "A");
        join(noTimer.code(), "Beto", "B");
        presenterPost(noTimer, "/start").andExpect(status().isOk());
        timer(noTimer, "START").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIMER_DISABLED"));
    }

    @Test
    @DisplayName("spec 008 AC-122 cambiar pregunta: 200 con otra pregunta en FACE_OFF; 409 en LOBBY y tras revelar; 401/403")
    void changeQuestion() throws Exception {
        Created c = createRoom("{\"rounds\":3}");
        join(c.code(), "Ana", "A");
        join(c.code(), "Beto", "B");
        presenterPost(c, "/rounds/current/question").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        JsonNode started = body(presenterPost(c, "/start").andExpect(status().isOk()));
        String before = started.get("round").get("question").asString();

        mvc.perform(post("/api/rooms/" + c.code() + "/rounds/current/question"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/rooms/" + c.code() + "/rounds/current/question").header(PT, "nope"))
                .andExpect(status().isForbidden());

        JsonNode changed = body(presenterPost(c, "/rounds/current/question").andExpect(status().isOk()));
        assertThat(changed.get("status").asString()).isEqualTo("FACE_OFF");
        assertThat(changed.get("round").get("question").asString()).isNotEqualTo(before);
        assertThat(changed.get("round").get("number").asInt()).isEqualTo(1);
        mvc.perform(get("/api/rooms/" + c.code()))
                .andExpect(jsonPath("$.round.question").value(changed.get("round").get("question").asString()));

        mvc.perform(post("/api/rooms/" + c.code() + "/rounds/current/control").header(PT, c.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"team\":\"A\"}")).andExpect(status().isOk());
        presenterPost(c, "/rounds/current/question").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("spec 005 AC-91 DELETE /api/rooms/{code}: 401 sin token, 403 token ajeno, 204 y después 404")
    void closeRoom() throws Exception {
        Created c = createRoom("{}");
        Created other = createRoom("{}");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/rooms/" + c.code()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/rooms/" + c.code())
                        .header(PT, other.token()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/rooms/" + c.code())
                        .header(PT, c.token()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/rooms/" + c.code())).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/rooms/" + c.code())
                        .header(PT, c.token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        mvc.perform(get("/api/rooms/" + other.code())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("spec 002 AC-35 AC-40…AC-43 AC-47 X sin buzz → 409 NO_ACTIVE_TEAM; X al equipo en turno y strikesByTeam")
    void turnAndStrikes() throws Exception {
        Created c = createRoom("{\"turnSeconds\":30}");
        join(c.code(), "Ana", "A");
        join(c.code(), "Beto", "B");
        JsonNode started = body(presenterPost(c, "/start").andExpect(status().isOk()));
        JsonNode round = started.get("round");
        assertThat(round.has("activeTeam") && round.get("activeTeam").isNull()).isTrue();
        assertThat(round.has("turnDeadline") && round.get("turnDeadline").isNull()).isTrue();
        assertThat(round.get("strikesByTeam").get("A").asInt()).isZero();

        presenterPost(c, "/rounds/current/strike").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ACTIVE_TEAM"));

        mvc.perform(post("/api/rooms/" + c.code() + "/rounds/current/control").header(PT, c.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"team\":\"B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round.activeTeam").value("B"))
                .andExpect(jsonPath("$.round.turnDeadline").value(org.hamcrest.Matchers.nullValue())) // spec 005 AC-79
                .andExpect(jsonPath("$.round.timerRunning").value(false))
                .andExpect(jsonPath("$.round.timerRemainingMs").value(30_000));
        timer(c, "START").andExpect(status().isOk())
                .andExpect(jsonPath("$.round.turnDeadline").value(org.hamcrest.Matchers.matchesPattern(
                        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?Z$")));
        JsonNode afterStrike = body(presenterPost(c, "/rounds/current/strike").andExpect(status().isOk()));
        assertThat(afterStrike.get("round").get("strikes").asInt()).isEqualTo(1);
        assertThat(afterStrike.get("round").get("strikesByTeam").get("B").asInt()).isEqualTo(1);
        assertThat(afterStrike.get("round").get("strikesByTeam").get("A").asInt()).isZero();
        java.time.Instant deadline = java.time.Instant.parse(afterStrike.get("round").get("turnDeadline").asString());
        assertThat(deadline).isBetween(java.time.Instant.now().plusSeconds(25), java.time.Instant.now().plusSeconds(31));
        mvc.perform(get("/api/rooms/" + c.code())).andExpect(jsonPath("$.round.activeTeam").value("B"))
                .andExpect(jsonPath("$.round.strikesByTeam.B").value(1));
    }

    @Test
    @DisplayName("spec 002 AC-53 POST /sound: token requerido, body validado, soundEnabled y versión")
    void sound() throws Exception {
        Created c = createRoom(null);
        mvc.perform(post("/api/rooms/" + c.code() + "/sound").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/rooms/" + c.code() + "/sound").header(PT, "nope")
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/rooms/ZZZZ/sound").header(PT, c.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/rooms/" + c.code() + "/sound").header(PT, c.token())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        int before = body(mvc.perform(get("/api/rooms/" + c.code()))).get("version").asInt();
        mvc.perform(post("/api/rooms/" + c.code() + "/sound").header(PT, c.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soundEnabled").value(false))
                .andExpect(jsonPath("$.version").value(before + 1))
                .andExpect(jsonPath("$.presenterBoard").isArray());
        mvc.perform(get("/api/rooms/" + c.code())).andExpect(jsonPath("$.soundEnabled").value(false));
    }
}
