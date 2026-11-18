package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** US-1 Crear sala — createRoom, getRoom, getPresenterView, getNetworkInfo (AC-01, AC-02, AC-04). */
@Tag("acceptance")
@DisplayName("Aceptación · Crear sala")
class RoomCreationAcceptanceTest extends AcceptanceTestBase {

    @Test
    @DisplayName("AC-01 createRoom devuelve 201 con código de 4 letras sin I/O, presenterToken, presenterClaimToken y estado LOBBY")
    void createRoomReturnsCodeAndTokens() {
        Room room = createRoom("{}");

        assertThat(room.code()).matches(ROOM_CODE_PATTERN);
        assertThat(room.presenterToken()).isNotBlank();
        assertThat(room.claimToken()).isNotBlank();
        assertThat(room.claimToken()).as("el token de canje es distinto del presenterToken (plan D10)")
                .isNotEqualTo(room.presenterToken());
        Resp created = room.created();
        assertThat(created.contentType()).startsWith("application/json");
        assertThat((String) created.read("$.state.code")).isEqualTo(room.code());
        assertThat((String) created.read("$.state.status")).isEqualTo("LOBBY");
        assertThat((Boolean) created.read("$.state.presenterConnected")).isFalse();
        assertThat(created.intAt("$.state.version")).isNotNegative();
        assertThat((List<?>) created.read("$.state.teams")).hasSize(2);
    }

    @Test
    @DisplayName("AC-01 los códigos son únicos entre las salas activas")
    void codesAreUniqueAmongActiveRooms() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            String code = createRoom("{}").code();
            assertThat(code).matches(ROOM_CODE_PATTERN);
            assertThat(codes.add(code)).as("código repetido %s", code).isTrue();
        }
    }

    @Test
    @DisplayName("AC-01 la sala creada se puede consultar por GET /api/rooms/{code} y GET presenter-view")
    void createdRoomIsRetrievable() {
        Room room = createRoom("{}");

        Resp pub = expectStatus(getRoom(room.code()), 200);
        assertThat((String) pub.read("$.code")).isEqualTo(room.code());
        assertThat((String) pub.read("$.status")).isEqualTo("LOBBY");

        Resp pv = expectStatus(get("/api/rooms/" + room.code() + "/presenter-view", presenter(room.presenterToken())), 200);
        assertThat((String) pv.read("$.code")).isEqualTo(room.code());
    }

    @Test
    @DisplayName("AC-01 GET de una sala inexistente → 404 ROOM_NOT_FOUND")
    void unknownRoomIs404() {
        // Probabilidad de colisión con una sala activa real: despreciable en el contexto de pruebas.
        expectProblem(getRoom("QZQZ"), 404, "ROOM_NOT_FOUND");
    }

    @Test
    @DisplayName("AC-02 sin cuerpo se usan los valores por defecto: Equipo Rojo / Equipo Verde, 5 rondas, meta 300")
    void defaultsWithoutBody() {
        Resp r = expectStatus(post("/api/rooms", null), 201);
        assertDefaults(r);
    }

    @Test
    @DisplayName("AC-02 con cuerpo vacío {} se usan los valores por defecto")
    void defaultsWithEmptyBody() {
        assertDefaults(createRoom("{}").created());
    }

    private void assertDefaults(Resp r) {
        assertThat(r.intAt("$.state.totalRounds")).isEqualTo(5);
        assertThat(r.intAt("$.state.targetScore")).isEqualTo(300);
        assertThat(r.<String>list("$.state.teams[?(@.id == 'A')].name")).containsExactly("Equipo Rojo");
        assertThat(r.<String>list("$.state.teams[?(@.id == 'B')].name")).containsExactly("Equipo Verde");
        assertThat(r.<Integer>list("$.state.teams[*].score")).containsOnly(0);
    }

    @Test
    @DisplayName("AC-02 el presentador configura nombres de equipo, rondas y meta")
    void customSettings() {
        Room room = createRoom("{\"teamAName\":\"Los Tlacuaches\",\"teamBName\":\"Las Ajolotas\",\"rounds\":3,\"targetScore\":150}");
        Resp pub = expectStatus(getRoom(room.code()), 200);

        assertThat(pub.intAt("$.totalRounds")).isEqualTo(3);
        assertThat(pub.intAt("$.targetScore")).isEqualTo(150);
        assertThat(pub.<String>list("$.teams[?(@.id == 'A')].name")).containsExactly("Los Tlacuaches");
        assertThat(pub.<String>list("$.teams[?(@.id == 'B')].name")).containsExactly("Las Ajolotas");
    }

    @ParameterizedTest(name = "AC-02 rondas en los límites válidos: {0}")
    @ValueSource(ints = {3, 8})
    void roundsBoundariesAccepted(int rounds) {
        assertThat(createRoom(rounds, 300).created().intAt("$.state.totalRounds")).isEqualTo(rounds);
    }

    @ParameterizedTest(name = "AC-02 cuerpo inválido → 400: {0}")
    @ValueSource(strings = {
        "{\"rounds\":2}",
        "{\"rounds\":9}",
        "{\"targetScore\":49}",
        "{\"targetScore\":1001}",
        "{\"teamAName\":\"\"}",
        "{\"teamBName\":\"1234567890123456789012345\"}"
    })
    void invalidSettingsRejected(String body) {
        Resp r = post("/api/rooms", body);
        assertThat(r.status()).as("respuesta: %s", r).isEqualTo(400);
        assertThat(r.contentType()).startsWith("application/problem+json");
    }

    @Test
    @DisplayName("AC-04 GET /api/network-info devuelve lanAddress, backendPort y frontendPort")
    void networkInfo() {
        Resp r = expectStatus(get("/api/network-info"), 200);
        assertThat((String) r.read("$.lanAddress")).isNotBlank();
        assertThat(r.intAt("$.backendPort")).isPositive();
        assertThat(r.intAt("$.frontendPort")).isPositive();
    }
}
