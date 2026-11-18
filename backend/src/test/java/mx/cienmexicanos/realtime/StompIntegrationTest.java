package mx.cienmexicanos.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T301/T302 contra el servidor real (puerto aleatorio): autenticación STOMP, difusión y buzz. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StompIntegrationTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final long TIMEOUT = 5;

    @LocalServerPort int port;
    @Autowired WebApplicationContext context;

    MockMvc mvc;
    WebSocketStompClient client;
    final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new CompositeMessageConverter(
                List.of(new StringMessageConverter(), new JacksonJsonMessageConverter())));
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(s -> {
            if (s.isConnected()) {
                s.disconnect();
            }
        });
        client.stop();
    }

    // ------------------------------------------------------------------ helpers

    /** Manejador que guarda los frames ERROR. */
    static final class Handler extends StompSessionHandlerAdapter {
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errors.add(headers.getFirst("message") + " " + payload);
        }
    }

    record Conn(StompSession session, Handler handler) {
        BlockingQueue<JsonNode> subscribe(String destination) {
            BlockingQueue<JsonNode> queue = new LinkedBlockingQueue<>();
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return JsonNode.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    queue.add((JsonNode) payload);
                }
            });
            return queue;
        }
    }

    Conn connect(String header, String token) throws Exception {
        StompHeaders connect = new StompHeaders();
        if (header != null) {
            connect.add(header, token);
        }
        Handler handler = new Handler();
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connect, handler).get(TIMEOUT, TimeUnit.SECONDS);
        sessions.add(session);
        return new Conn(session, handler);
    }

    JsonNode postJson(String path, String presenterToken, String body, int expected) throws Exception {
        var req = post(path);
        if (presenterToken != null) {
            req.header("X-Presenter-Token", presenterToken);
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String json = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return JSON.readTree(json);
    }

    static JsonNode next(BlockingQueue<JsonNode> queue) throws InterruptedException {
        JsonNode node = queue.poll(TIMEOUT, TimeUnit.SECONDS);
        assertThat(node).as("mensaje esperado").isNotNull();
        return node;
    }

    /** Lee hasta encontrar un evento del tipo indicado. */
    static JsonNode nextEvent(BlockingQueue<JsonNode> queue, String type) throws InterruptedException {
        while (true) {
            JsonNode e = next(queue);
            if (type.equals(e.get("type").asString())) {
                return e;
            }
        }
    }

    /** Lee snapshots hasta uno con el estado indicado; verifica que la versión crezca. */
    static JsonNode nextState(BlockingQueue<JsonNode> queue, String status, long[] lastVersion)
            throws InterruptedException {
        while (true) {
            JsonNode s = next(queue);
            long v = s.get("version").asLong();
            assertThat(v).as("version creciente").isGreaterThan(lastVersion[0]);
            lastVersion[0] = v;
            if (status.equals(s.get("status").asString())) {
                return s;
            }
        }
    }

    // ------------------------------------------------------------------ pruebas

    @Test
    @DisplayName("AC-08 AC-13 AC-14 AC-25 AC-27 lobby en vivo, buzz del designado, errores a /user/queue/errors")
    void lobbyAndBuzz() throws Exception {
        JsonNode created = postJson("/api/rooms", null, null, 201);
        String code = created.get("code").asString();
        String host = created.get("presenterToken").asString();

        Conn tv = connect("X-Presenter-Token", host);
        BlockingQueue<JsonNode> tvState = tv.subscribe("/topic/rooms/" + code + "/state");
        BlockingQueue<JsonNode> tvPresenter = tv.subscribe("/topic/rooms/" + code + "/presenter");
        BlockingQueue<JsonNode> events = tv.subscribe("/topic/rooms/" + code + "/events");
        Thread.sleep(200); // las suscripciones viajan antes del primer cambio

        // AC-08: el lobby recibe al jugador que se une
        JsonNode ana = postJson("/api/rooms/" + code + "/players", null, "{\"name\":\"Ana\",\"team\":\"A\"}", 201);
        long[] v = {0};
        JsonNode lobby = nextState(tvState, "LOBBY", v);
        assertThat(lobby.get("teams").get(0).get("players").get(0).get("name").asString()).isEqualTo("Ana");
        assertThat(nextEvent(events, "PLAYER_JOINED").get("playerId").asString())
                .isEqualTo(ana.get("player").get("id").asString());
        JsonNode beto = postJson("/api/rooms/" + code + "/players", null, "{\"name\":\"Beto\",\"team\":\"B\"}", 201);
        JsonNode carla = postJson("/api/rooms/" + code + "/players", null, "{\"name\":\"Carla\",\"team\":\"A\"}", 201);

        Conn anaWs = connect("X-Player-Token", ana.get("playerToken").asString());
        Conn betoWs = connect("X-Player-Token", beto.get("playerToken").asString());
        Conn carlaWs = connect("X-Player-Token", carla.get("playerToken").asString());
        BlockingQueue<JsonNode> carlaErrors = carlaWs.subscribe("/user/queue/errors");
        BlockingQueue<JsonNode> anaErrors = anaWs.subscribe("/user/queue/errors");
        BlockingQueue<JsonNode> anaState = anaWs.subscribe("/topic/rooms/" + code + "/state");
        Thread.sleep(200);

        // AC-27: fuera de FACE_OFF → INVALID_STATE
        anaWs.session().send("/app/rooms/" + code + "/buzz", "{}");
        assertThat(next(anaErrors).get("code").asString()).isEqualTo("INVALID_STATE");

        postJson("/api/rooms/" + code + "/start", host, null, 200);
        JsonNode faceOff = nextState(tvState, "FACE_OFF", v);
        JsonNode presenterFaceOff = next(tvPresenter);
        while (!"FACE_OFF".equals(presenterFaceOff.get("status").asString())) {
            presenterFaceOff = next(tvPresenter);
        }
        assertThat(presenterFaceOff.get("presenterBoard").get(0).get("text").isString()).isTrue();

        // AC-25: el snapshot público que recibe el jugador no trae respuestas ocultas
        JsonNode playerView = next(anaState);
        while (!"FACE_OFF".equals(playerView.get("status").asString())) {
            playerView = next(anaState);
        }
        String hiddenText = presenterFaceOff.get("presenterBoard").get(0).get("text").asString();
        assertThat(playerView.toString()).doesNotContain(hiddenText).doesNotContain("presenterBoard");
        assertThat(faceOff.get("round").get("faceOff").get("playerA").asString())
                .isEqualTo(ana.get("player").get("id").asString());

        // AC-27: Carla no es la designada → NOT_YOUR_TURN
        carlaWs.session().send("/app/rooms/" + code + "/buzz", "{}");
        assertThat(next(carlaErrors).get("code").asString()).isEqualTo("NOT_YOUR_TURN");

        // AC-14: Beto gana; el buzz posterior de Ana se ignora
        betoWs.session().send("/app/rooms/" + code + "/buzz", "{}");
        JsonNode won = nextEvent(events, "BUZZ_WON");
        assertThat(won.get("playerId").asString()).isEqualTo(beto.get("player").get("id").asString());
        assertThat(won.get("team").asString()).isEqualTo("B");
        // spec 002 AC-35/AC-36: el equipo del buzz queda en turno
        assertThat(nextEvent(events, "TURN_CHANGED").get("team").asString()).isEqualTo("B");
        anaWs.session().send("/app/rooms/" + code + "/buzz", "{}");
        JsonNode afterBuzz = nextState(tvState, "FACE_OFF", v);
        assertThat(afterBuzz.get("round").get("faceOff").get("buzzWinner").asString())
                .isEqualTo(beto.get("player").get("id").asString());
        assertThat(events.poll(500, TimeUnit.MILLISECONDS)).as("sin un segundo BUZZ_WON").isNull();
        assertThat(anaErrors.poll(300, TimeUnit.MILLISECONDS)).as("el buzz tardío no es error").isNull();
    }

    @Test
    @DisplayName("AC-26 C-8 sin presenter token no se puede suscribir a /presenter; CONNECT sin token o inválido se rechaza")
    void presenterChannelIsProtected() throws Exception {
        JsonNode created = postJson("/api/rooms", null, null, 201);
        String code = created.get("code").asString();
        JsonNode ana = postJson("/api/rooms/" + code + "/players", null, "{\"name\":\"Ana\",\"team\":\"A\"}", 201);

        Conn player = connect("X-Player-Token", ana.get("playerToken").asString());
        player.subscribe("/topic/rooms/" + code + "/presenter");
        assertThat(player.handler().errors.poll(TIMEOUT, TimeUnit.SECONDS)).contains("FORBIDDEN");

        Conn wildcard = connect("X-Player-Token", ana.get("playerToken").asString());
        wildcard.subscribe("/topic/rooms/*/presenter");
        assertThat(wildcard.handler().errors.poll(TIMEOUT, TimeUnit.SECONDS)).contains("FORBIDDEN");

        // Otra sala: el token de presentador no vale para esta
        JsonNode other = postJson("/api/rooms", null, null, 201);
        Conn otherPresenter = connect("X-Presenter-Token", other.get("presenterToken").asString());
        otherPresenter.subscribe("/topic/rooms/" + code + "/presenter");
        assertThat(otherPresenter.handler().errors.poll(TIMEOUT, TimeUnit.SECONDS)).contains("FORBIDDEN");

        // C-8: CONNECT sin token o con token inválido se rechaza
        assertThatThrownBy(() -> connect("X-Presenter-Token", "nope")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connect("X-Player-Token", "nope")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connect(null, null)).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("AC-32 la pantalla ve presenterConnected y PRESENTER_CLAIMED; buzz desde sesión de presentador → UNAUTHORIZED")
    void claimBroadcastAndAnonymousBuzz() throws Exception {
        JsonNode created = postJson("/api/rooms", null, null, 201);
        String code = created.get("code").asString();
        Conn tv = connect("X-Presenter-Token", created.get("presenterToken").asString());
        BlockingQueue<JsonNode> state = tv.subscribe("/topic/rooms/" + code + "/state");
        BlockingQueue<JsonNode> events = tv.subscribe("/topic/rooms/" + code + "/events");
        BlockingQueue<JsonNode> errors = tv.subscribe("/user/queue/errors");
        Thread.sleep(200);

        postJson("/api/rooms/" + code + "/presenter/claim", null,
                "{\"claimToken\":\"" + created.get("presenterClaimToken").asString() + "\"}", 200);
        assertThat(next(state).get("presenterConnected").asBoolean()).isTrue();
        assertThat(nextEvent(events, "PRESENTER_CLAIMED").get("version").asLong()).isPositive();

        tv.session().send("/app/rooms/" + code + "/buzz", "{}");
        assertThat(next(errors).get("code").asString()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("AC-09 el jugador aparece desconectado al cerrar su sesión STOMP (PLAYER_LEFT)")
    void playerLeft() throws Exception {
        JsonNode created = postJson("/api/rooms", null, null, 201);
        String code = created.get("code").asString();
        JsonNode ana = postJson("/api/rooms/" + code + "/players", null, "{\"name\":\"Ana\",\"team\":\"A\"}", 201);
        Conn tv = connect("X-Presenter-Token", created.get("presenterToken").asString());
        BlockingQueue<JsonNode> events = tv.subscribe("/topic/rooms/" + code + "/events");
        Thread.sleep(200);

        Conn anaWs = connect("X-Player-Token", ana.get("playerToken").asString());
        Thread.sleep(200);
        anaWs.session().disconnect();
        assertThat(nextEvent(events, "PLAYER_LEFT").get("playerId").asString())
                .isEqualTo(ana.get("player").get("id").asString());
    }
}
