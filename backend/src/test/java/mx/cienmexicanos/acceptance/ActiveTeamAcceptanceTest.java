package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Spec 002 US-11 — Equipo en turno: {@code round.activeTeam} según la tabla de AC-35 y el evento {@code TURN_CHANGED}
 * (AC-36). T721. Salas sin temporizador ({@code turnSeconds=0}) para que ninguna X automática altere el turno.
 */
@Tag("acceptance")
@DisplayName("Aceptación 002 · Equipo en turno")
class ActiveTeamAcceptanceTest extends AcceptanceTestBase {

    /** Comprueba {@code activeTeam} en la vista del presentador y en la pública (RoomState de jugadores y TV). */
    private void expectActive(Game g, String expected, String when) {
        Resp pv = presenterView(g);
        Resp pub = expectStatus(getRoom(g.code()), 200);
        assertRoundKeysPresent(pv);
        assertRoundKeysPresent(pub);
        assertThat(activeTeam(pv)).as("AC-35 activeTeam (presentador) %s", when).isEqualTo(expected);
        assertThat(activeTeam(pub)).as("AC-35 activeTeam (público) %s", when).isEqualTo(expected);
    }

    @Test
    @DisplayName("AC-35 LOBBY: activeTeam null (sin ronda)")
    void lobbyHasNoActiveTeam() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        expectActive(g, null, "en LOBBY");
    }

    @Test
    @DisplayName("AC-35 FACE_OFF sin buzz: activeTeam null")
    void faceOffWithoutBuzzHasNoActiveTeam() {
        Game g = startedGame(3, 1000);
        Resp pv = presenterView(g);
        assertThat(pv.roomStatus()).isEqualTo("FACE_OFF");
        assertThat((Object) pv.readOrNull("$.round.faceOff.buzzWinner")).isNull();
        expectActive(g, null, "en FACE_OFF sin buzz");
    }

    @Test
    @DisplayName("AC-35 FACE_OFF con buzz: activeTeam = equipo del ganador del buzz (A y B)")
    void faceOffBuzzWinnerTeamIsActive() {
        Game a = startedGame(3, 1000);
        buzzBy(a, "A");
        expectActive(a, "A", "tras el buzz de A");

        Game b = startedGame(3, 1000);
        buzzBy(b, "B");
        expectActive(b, "B", "tras el buzz de B");
    }

    @Test
    @DisplayName("spec 006 AC-93 AC-95 FACE_OFF: el primero abre la #2 → turno de B; B abre la #1 (más alta) → control B")
    void faceOffRevealPassesTurn() {
        Game g = startedGame(3, 1000);
        Slot top = slotAt(g, 1);
        Slot second = slotAt(g, 2);
        buzzBy(g, "A");

        Resp r1 = expectStatus(reveal(g, second.answerId()), 200);
        assertThat(r1.roomStatus()).isEqualTo("FACE_OFF");
        assertThat(activeTeam(r1)).as("A respondió la #2 → turno de B").isEqualTo("B");
        assertThat((String) r1.read("$.round.faceOff.firstTeam")).isEqualTo("A");
        assertThat(r1.intAt("$.round.faceOff.firstAnswerPosition")).isEqualTo(2);

        Resp r2 = expectStatus(reveal(g, top.answerId()), 200);
        assertThat(r2.roomStatus()).as("AC-95 termina el cara a cara").isEqualTo("PLAYING");
        assertThat((String) r2.read("$.round.controllingTeam")).isEqualTo("B");
        assertThat(activeTeam(r2)).as("PLAYING: activeTeam = controllingTeam").isEqualTo("B");
    }

    @Test
    @DisplayName("spec 006 AC-94 AC-97 FACE_OFF: el primer fallo pasa el turno (B → A); el segundo termina el cara a cara con control B")
    void faceOffStrikePassesTurn() {
        Game g = startedGame(3, 1000);
        buzzBy(g, "B");
        Resp x1 = expectStatus(strike(g), 200);
        assertThat(x1.roomStatus()).isEqualTo("FACE_OFF");
        assertThat(activeTeam(x1)).isEqualTo("A");
        Resp x2 = expectStatus(strike(g), 200);
        assertThat(x2.roomStatus()).isEqualTo("PLAYING");
        assertThat((String) x2.read("$.round.controllingTeam")).isEqualTo("B");
        assertThat(x2.intAt("$.round.strikes")).as("los fallos del cara a cara no son X").isZero();
        expectActive(g, "B", "tras dos fallos en el cara a cara");
    }

    @Test
    @DisplayName("AC-35 PLAYING: activeTeam = controllingTeam aunque haya revelaciones y X")
    void playingActiveIsControllingTeam() {
        Game g = startedGame(3, 1000);
        Slot second = slotAt(g, 2);
        Resp control = winFaceOff(g, "A");
        assertThat(activeTeam(control)).isEqualTo("A");

        Resp afterReveal = expectStatus(reveal(g, second.answerId()), 200);
        assertThat(afterReveal.roomStatus()).isEqualTo("PLAYING");
        assertThat(activeTeam(afterReveal)).as("revelar en PLAYING no pasa el turno").isEqualTo("A");

        Resp afterX = expectStatus(strike(g), 200);
        assertThat(afterX.roomStatus()).isEqualTo("PLAYING");
        assertThat(activeTeam(afterX)).as("una X en PLAYING no pasa el turno").isEqualTo("A");
        expectActive(g, "A", "en PLAYING");
    }

    @Test
    @DisplayName("AC-35 STEAL: activeTeam = el equipo rival (el que roba)")
    void stealActiveIsRival() {
        Game g = startedGame(3, 1000);
        winFaceOff(g, "B");
        Resp steal = strikeUntilSteal(g);
        assertThat(steal.roomStatus()).isEqualTo("STEAL");
        assertThat((String) steal.read("$.round.controllingTeam")).isEqualTo("B");
        assertThat(activeTeam(steal)).isEqualTo("A");
        expectActive(g, "A", "en STEAL");
    }

    @Test
    @DisplayName("AC-35 ROUND_END y GAME_OVER: activeTeam null")
    void roundEndAndGameOverHaveNoActiveTeam() {
        Game g = startedGame(3, 1000);
        playRoundKeepingPot(g, "A");
        expectActive(g, null, "en ROUND_END");

        expectStatus(nextRound(g), 200);
        expectActive(g, null, "en FACE_OFF sin buzz de la ronda 2");
        playRoundWithEmptyPot(g, "B");
        expectStatus(nextRound(g), 200);
        playRoundWithEmptyPot(g, "A");
        Resp over = expectStatus(nextRound(g), 200);
        assertThat(over.roomStatus()).isEqualTo("GAME_OVER");
        expectActive(g, null, "en GAME_OVER");
    }

    @Test
    @DisplayName("AC-35 nextRound: la ronda nueva empieza en FACE_OFF sin equipo en turno")
    void nextRoundResetsActiveTeam() {
        Game g = startedGame(3, 1000);
        playRoundKeepingPot(g, "A");
        Resp next = expectStatus(nextRound(g), 200);
        assertThat(next.roomStatus()).isEqualTo("FACE_OFF");
        assertThat(activeTeam(next)).isNull();
    }

    // ------------------------------------------------------------------ AC-36

    /**
     * Ejecuta {@code command} y espera el TURN_CHANGED del equipo {@code expectedTeam}, con {@code version} mayor a la
     * previa, y que el snapshot difundido en {@code /state} ya lo refleje.
     */
    private Resp expectTurnChanged(Game g, Watch w, Supplier<Resp> command, String expectedTeam) {
        int before = presenterView(g).intAt("$.version");
        int mark = w.mark();
        Resp r = expectStatus(command.get(), 200);
        assertThat(r.intAt("$.version")).as("AC-36 la versión sube").isGreaterThan(before);
        String event = w.awaitEvent(mark, "TURN_CHANGED", TIMEOUT);
        assertThat((String) JsonPath.read(event, "$.team")).as("AC-36 TURN_CHANGED.team").isEqualTo(expectedTeam);
        assertThat(((Number) JsonPath.read(event, "$.version")).intValue()).as("AC-36 TURN_CHANGED.version").isGreaterThan(before);
        w.state().await(json -> expectedTeam.equals(activeTeam(json))
                && ((Number) JsonPath.read(json, "$.version")).intValue() >= r.intAt("$.version"), TIMEOUT);
        return r;
    }

    @Test
    @DisplayName("AC-36 TURN_CHANGED con team en cada cambio: buzz → revelar → revelar (fin del cara a cara) → robo")
    void turnChangedOnEveryChange() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        try (Watch w = watch(g)) {
            int mark = w.mark();
            expectStatus(start(g), 200);
            Slot top = slotAt(g, 1);
            Slot second = slotAt(g, 2);

            expectTurnChanged(g, w, () -> {
                buzzBy(g, "A");
                return presenterView(g);
            }, "A");
            expectTurnChanged(g, w, () -> reveal(g, second.answerId()), "B");
            Resp control = expectStatus(reveal(g, top.answerId()), 200); // spec 006 AC-95: B gana; sigue en turno
            assertThat(control.roomStatus()).isEqualTo("PLAYING");
            assertThat(activeTeam(control)).isEqualTo("B");
            expectStatus(strike(g), 200);
            expectStatus(strike(g), 200);
            Resp steal = expectTurnChanged(g, w, () -> strike(g), "A");
            assertThat(steal.roomStatus()).isEqualTo("STEAL");

            List<String> teams = w.eventsOf("TURN_CHANGED", mark).stream()
                    .map(json -> AcceptanceTestBase.<String>jsonAt(json, "$.team"))
                    .filter(t -> t != null)
                    .toList();
            assertThat(teams)
                    .as("secuencia de TURN_CHANGED desde el inicio: iniciar no da turno; ningún cambio se publica dos veces")
                    .containsExactly("A", "B", "A"); // spec 006: B gana el control sin cambio de turno
        }
    }

    @Test
    @DisplayName("AC-36 sin cambio de equipo en turno no se publica TURN_CHANGED (revelar y X en PLAYING)")
    void noTurnChangedWithoutChange() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        try (Watch w = watch(g)) {
            expectStatus(start(g), 200);
            Slot second = slotAt(g, 2);
            Resp control = winFaceOff(g, "A");
            w.awaitStateVersion(control.intAt("$.version"), TIMEOUT);
            int mark = w.mark();

            expectStatus(reveal(g, second.answerId()), 200);
            expectStatus(strike(g), 200);
            // Barrera: el STRIKE de la X se publica después de cualquier evento del revelar previo.
            w.awaitEvent(mark, "STRIKE", TIMEOUT);
            assertThat(w.eventsOf("TURN_CHANGED", mark)).as("activeTeam siguió siendo A").isEmpty();
        }
    }
}
