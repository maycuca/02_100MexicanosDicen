package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** US-10 QR de presentador: canje de un solo uso, regeneración y presenterConnected (AC-30, AC-32, AC-33). */
@Tag("acceptance")
@DisplayName("Aceptación · QR de presentador")
class PresenterClaimAcceptanceTest extends AcceptanceTestBase {

    private Resp claim(String code, String claimToken) {
        return post("/api/rooms/" + code + "/presenter/claim", "{\"claimToken\":" + quote(claimToken) + "}");
    }

    private Resp regenerate(Room room) {
        return post("/api/rooms/" + room.code() + "/presenter/claim-token", null, presenter(room.presenterToken()));
    }

    @Test
    @DisplayName("AC-30 canjear el claimToken devuelve un presenterToken válido para la sala")
    void claimReturnsWorkingPresenterToken() {
        Room room = createRoom("{}");
        Resp r = expectStatus(claim(room.code(), room.claimToken()), 200);

        String phoneToken = r.read("$.presenterToken");
        assertThat(phoneToken).isNotBlank();
        assertThat(phoneToken).as("el token canjeado no es el claimToken").isNotEqualTo(room.claimToken());
        assertThat((String) r.read("$.state.code")).isEqualTo(room.code());
        assertThat((Boolean) r.read("$.state.presenterConnected")).isTrue();

        expectStatus(get("/api/rooms/" + room.code() + "/presenter-view", presenter(phoneToken)), 200);
        // El celular del presentador puede controlar la partida.
        join(room.code(), uniqueName("Ana"), "A");
        join(room.code(), uniqueName("Beto"), "B");
        Resp started = expectStatus(post("/api/rooms/" + room.code() + "/start", null, presenter(phoneToken)), 200);
        assertThat(started.roomStatus()).isEqualTo("FACE_OFF");
    }

    @Test
    @DisplayName("AC-30 un segundo canje del mismo claimToken → 409 CLAIM_USED")
    void claimIsSingleUse() {
        Room room = createRoom("{}");
        expectStatus(claim(room.code(), room.claimToken()), 200);
        expectProblem(claim(room.code(), room.claimToken()), 409, "CLAIM_USED");
    }

    @Test
    @DisplayName("AC-30 un claimToken inválido → 403 CLAIM_INVALID")
    void invalidClaim() {
        Room room = createRoom("{}");
        expectProblem(claim(room.code(), "no-es-un-claim"), 403, "CLAIM_INVALID");
        assertThat((Boolean) getRoom(room.code()).read("$.presenterConnected")).isFalse();
    }

    @Test
    @DisplayName("AC-30 el claimToken de otra sala no sirve → 403 CLAIM_INVALID")
    void claimFromOtherRoom() {
        Room a = createRoom("{}");
        Room b = createRoom("{}");
        expectProblem(claim(a.code(), b.claimToken()), 403, "CLAIM_INVALID");
    }

    @Test
    @DisplayName("AC-30 canje en sala inexistente → 404 ROOM_NOT_FOUND")
    void claimUnknownRoom() {
        expectProblem(claim("QZQZ", "x"), 404, "ROOM_NOT_FOUND");
    }

    @Test
    @DisplayName("AC-32 presenterConnected es false al crear y true tras el canje (REST público)")
    void presenterConnectedFlag() {
        Room room = createRoom("{}");
        assertThat((Boolean) getRoom(room.code()).read("$.presenterConnected")).isFalse();
        int v = getRoom(room.code()).intAt("$.version");

        expectStatus(claim(room.code(), room.claimToken()), 200);
        Resp after = getRoom(room.code());
        assertThat((Boolean) after.read("$.presenterConnected")).isTrue();
        assertThat(after.intAt("$.version")).isGreaterThan(v);
    }

    @Test
    @DisplayName("AC-33 la laptop regenera el QR: nuevo claimToken válido y el anterior queda invalidado")
    void regenerateInvalidatesPrevious() {
        Room room = createRoom("{}");
        Resp r = expectStatus(regenerate(room), 200);
        String fresh = r.read("$.claimToken");
        assertThat(fresh).isNotBlank().isNotEqualTo(room.claimToken());

        Resp old = claim(room.code(), room.claimToken());
        assertThat(old.status()).as("canje del claim anterior: %s", old).isIn(403, 409);
        assertThat(old.code()).isIn("CLAIM_INVALID", "CLAIM_USED");

        expectStatus(claim(room.code(), fresh), 200);
    }

    @Test
    @DisplayName("AC-33 la laptop conserva su presenterToken después del canje y después de regenerar")
    void screenKeepsItsToken() {
        Room room = createRoom("{}");
        expectStatus(claim(room.code(), room.claimToken()), 200);
        expectStatus(get("/api/rooms/" + room.code() + "/presenter-view", presenter(room.presenterToken())), 200);

        Resp r = expectStatus(regenerate(room), 200);
        expectStatus(get("/api/rooms/" + room.code() + "/presenter-view", presenter(room.presenterToken())), 200);
        // Un presentador que cambia de celular puede canjear el nuevo QR.
        expectStatus(claim(room.code(), r.read("$.claimToken")), 200);
    }

    @Test
    @DisplayName("AC-33 regenerar invalida el presenterToken del celular anterior (→ 403) y pone presenterConnected = false")
    void regenerateRevokesPreviousPhone() {
        Room room = createRoom("{}");
        String oldPhone = expectStatus(claim(room.code(), room.claimToken()), 200).read("$.presenterToken");
        assertThat((Boolean) getRoom(room.code()).read("$.presenterConnected")).isTrue();

        String fresh = expectStatus(regenerate(room), 200).read("$.claimToken");

        assertThat((Boolean) getRoom(room.code()).read("$.presenterConnected")).isFalse();
        assertThat(get("/api/rooms/" + room.code() + "/presenter-view", presenter(oldPhone)).status()).isEqualTo(403);
        join(room.code(), uniqueName("Ana"), "A");
        join(room.code(), uniqueName("Beto"), "B");
        assertThat(post("/api/rooms/" + room.code() + "/start", null, presenter(oldPhone)).status()).isEqualTo(403);
        assertThat((String) getRoom(room.code()).read("$.status")).isEqualTo("LOBBY");

        // El token de la laptop sigue válido y el nuevo celular se conecta.
        expectStatus(get("/api/rooms/" + room.code() + "/presenter-view", presenter(room.presenterToken())), 200);
        String newPhone = expectStatus(claim(room.code(), fresh), 200).read("$.presenterToken");
        assertThat((Boolean) getRoom(room.code()).read("$.presenterConnected")).isTrue();
        expectStatus(get("/api/rooms/" + room.code() + "/presenter-view", presenter(newPhone)), 200);
    }

    @Test
    @DisplayName("AC-33 regenerar con el token del celular → también regenera (o se rechaza) pero nunca revoca a la laptop")
    void screenTokenSurvivesAnyRegeneration() {
        Room room = createRoom("{}");
        String phone = expectStatus(claim(room.code(), room.claimToken()), 200).read("$.presenterToken");
        post("/api/rooms/" + room.code() + "/presenter/claim-token", null, presenter(phone));
        expectStatus(get("/api/rooms/" + room.code() + "/presenter-view", presenter(room.presenterToken())), 200);
    }
}
