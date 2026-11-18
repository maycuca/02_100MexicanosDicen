package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Spec 002 US-12 — Strikes del equipo en turno: X a {@code activeTeam}, {@code strikesByTeam}, {@code NO_ACTIVE_TEAM}
 * y el evento {@code STRIKE} con {@code team}/{@code strikes} (AC-40…AC-43). T722. Salas con {@code turnSeconds=0}.
 */
@Tag("acceptance")
@DisplayName("Aceptación 002 · Strikes por equipo")
class TeamStrikesAcceptanceTest extends AcceptanceTestBase {

    private static void expectStrikesByTeam(Resp r, int a, int b) {
        assertRoundKeysPresent(r);
        assertThat(strikesOf(r, "A")).as("strikesByTeam.A en %s", r).isEqualTo(a);
        assertThat(strikesOf(r, "B")).as("strikesByTeam.B en %s", r).isEqualTo(b);
        assertStrikesInvariant(r);
    }

    private static void expectStrikeEvent(String event, String team, int strikes) {
        assertThat((String) JsonPath.read(event, "$.team")).as("AC-43 STRIKE.team en %s", event).isEqualTo(team);
        assertThat(((Number) JsonPath.read(event, "$.strikes")).intValue()).as("AC-43 STRIKE.strikes en %s", event).isEqualTo(strikes);
        assertThat((Object) JsonPath.read(event, "$.version")).isInstanceOf(Number.class);
    }

    // ------------------------------------------------------------------ AC-40

    @Test
    @DisplayName("AC-40 X en FACE_OFF sin buzz → 409 NO_ACTIVE_TEAM y la sala no cambia (C-2 actualizada)")
    void strikeWithoutBuzzIsNoActiveTeam() {
        Game g = startedGame(3, 1000);
        int version = presenterView(g).intAt("$.version");

        expectProblem(strike(g), 409, "NO_ACTIVE_TEAM");

        Resp after = presenterView(g);
        assertThat(after.roomStatus()).isEqualTo("FACE_OFF");
        assertThat(after.intAt("$.version")).as("un comando rechazado no cambia la versión").isEqualTo(version);
        assertThat(after.intAt("$.round.strikes")).isZero();
        expectStrikesByTeam(after, 0, 0);
    }

    @Test
    @DisplayName("AC-40 X sin buzz → 409 NO_ACTIVE_TEAM con Problem (application/problem+json)")
    void noActiveTeamIsProblemJson() {
        Game g = startedGame(3, 1000);
        Resp r = strike(g);
        expectProblem(r, 409, "NO_ACTIVE_TEAM");
        assertThat(r.contentType()).startsWith("application/problem+json");
    }

    @Test
    @DisplayName("spec 006 AC-100 C-28 sin buzz no se revela (409), pero el presentador puede corregir el control; después la X ya tiene equipo")
    void revealAndControlWithoutBuzzStillWork() {
        Game g = startedGame(3, 1000);
        expectProblem(reveal(g, slotAt(g, 1).answerId()), 409, "NO_ACTIVE_TEAM");
        Resp control = expectStatus(assignControl(g, "B"), 200);
        assertThat(control.roomStatus()).isEqualTo("PLAYING");
        assertThat(activeTeam(control)).isEqualTo("B");
        Resp x = expectStatus(strike(g), 200);
        expectStrikesByTeam(x, 0, 1);
    }

    // ------------------------------------------------------------------ AC-41

    @Test
    @DisplayName("AC-41 al iniciar la ronda strikesByTeam = {A:0, B:0}")
    void strikesByTeamStartsAtZero() {
        Game g = startedGame(3, 1000);
        expectStrikesByTeam(presenterView(g), 0, 0);
        expectStrikesByTeam(expectStatus(getRoom(g.code()), 200), 0, 0);
    }

    @Test
    @DisplayName("AC-40 AC-41 PLAYING: las X van al equipo con control (0–3) y strikesByTeam[controllingTeam] == strikes")
    void playingStrikesGoToControllingTeam() {
        Game g = startedGame(3, 1000);
        Resp control = winFaceOff(g, "B");
        expectStrikesByTeam(control, 0, 0);

        Resp x1 = expectStatus(strike(g), 200);
        expectStrikesByTeam(x1, 0, 1);
        Resp x2 = expectStatus(strike(g), 200);
        expectStrikesByTeam(x2, 0, 2);
        Resp x3 = expectStatus(strike(g), 200);
        assertThat(x3.roomStatus()).isEqualTo("STEAL");
        expectStrikesByTeam(x3, 0, 3);
        expectStrikesByTeam(expectStatus(getRoom(g.code()), 200), 0, 3);
    }

    @Test
    @DisplayName("AC-40 AC-41 STEAL: la X va al equipo que roba (0–1) y no altera las X del equipo con control")
    void stealStrikeGoesToRival() {
        Game g = startedGame(3, 1000);
        winFaceOff(g, "A");
        Resp steal = strikeUntilSteal(g);
        expectStrikesByTeam(steal, 3, 0);

        Resp end = expectStatus(strike(g), 200);
        assertThat(end.roomStatus()).isEqualTo("ROUND_END");
        assertThat((String) end.read("$.round.potWinner")).isEqualTo("A");
        assertThat(end.intAt("$.round.strikes")).isEqualTo(3);
        expectStrikesByTeam(end, 3, 1);
    }

    @Test
    @DisplayName("AC-41 spec 006 AC-97 los fallos del cara a cara no se acumulan; el segundo da el control al primero")
    void faceOffStrikesDoNotAccumulate() {
        Game g = startedGame(3, 1000);
        buzzBy(g, "A");
        Resp x1 = expectStatus(strike(g), 200);
        assertThat(x1.intAt("$.round.strikes")).isZero();
        expectStrikesByTeam(x1, 0, 0);
        Resp control = expectStatus(strike(g), 200);
        assertThat(control.roomStatus()).isEqualTo("PLAYING");
        assertThat((String) control.read("$.round.controllingTeam")).isEqualTo("A");
        assertThat(control.intAt("$.round.strikes")).isZero();
        expectStrikesByTeam(control, 0, 0);
    }

    @Test
    @DisplayName("AC-41 nextRound reinicia strikesByTeam a {A:0, B:0}")
    void nextRoundResetsStrikesByTeam() {
        Game g = startedGame(3, 1000);
        playRoundKeepingPot(g, "B");
        Resp next = expectStatus(nextRound(g), 200);
        assertThat(next.roomStatus()).isEqualTo("FACE_OFF");
        expectStrikesByTeam(next, 0, 0);
    }

    // ------------------------------------------------------------------ AC-42 / AC-43

    @Test
    @DisplayName("spec 005 AC-85 fallo en FACE_OFF: FACE_OFF_MISS (sin STRIKE ni X), el turno pasa al rival y regresa")
    void faceOffStrikeEvent() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        try (Watch w = watch(g)) {
            expectStatus(start(g), 200);
            buzzBy(g, "A");
            int mark = w.mark();
            Resp x = expectStatus(strike(g), 200);
            assertThat(x.roomStatus()).isEqualTo("FACE_OFF");
            assertThat(activeTeam(x)).as("AC-85 pasa el turno al rival").isEqualTo("B");
            expectStrikesByTeam(x, 0, 0);
            String miss = w.awaitEvent(mark, "FACE_OFF_MISS", TIMEOUT);
            assertThat((String) JsonPath.read(miss, "$.team")).isEqualTo("A");

            int mark2 = w.mark();
            Resp back = expectStatus(strike(g), 200);
            assertThat(activeTeam(back)).as("AC-85 si el rival también falla, regresa al primero").isEqualTo("A");
            expectStrikesByTeam(back, 0, 0);
            assertThat(back.intAt("$.round.strikes")).isZero();
            String miss2 = w.awaitEvent(mark2, "FACE_OFF_MISS", TIMEOUT);
            assertThat((String) JsonPath.read(miss2, "$.team")).isEqualTo("B");
            assertThat(w.eventsOf("STRIKE", mark)).as("AC-85 no hay X en el cara a cara").isEmpty();
        }
    }

    @Test
    @DisplayName("AC-43 STRIKE en PLAYING y STEAL: team y strikes (las X de ese equipo en strikesByTeam)")
    void strikeEventsCarryTeamAndCount() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        try (Watch w = watch(g)) {
            expectStatus(start(g), 200);
            winFaceOff(g, "A");
            for (int i = 1; i <= 3; i++) {
                int mark = w.mark();
                Resp x = expectStatus(strike(g), 200);
                expectStrikesByTeam(x, i, 0);
                expectStrikeEvent(w.awaitEvent(mark, "STRIKE", TIMEOUT), "A", i);
            }
            int mark = w.mark();
            Resp end = expectStatus(strike(g), 200); // robo fallido de B
            assertThat(end.roomStatus()).isEqualTo("ROUND_END");
            expectStrikeEvent(w.awaitEvent(mark, "STRIKE", TIMEOUT), "B", 1);
            assertThat(w.eventsOf("STRIKE", mark)).as("una sola X → un solo STRIKE").hasSize(1);
        }
    }

    @Test
    @DisplayName("AC-40 en STEAL la X es para el equipo en turno (el que roba), no para el que tiene el control")
    void stealStrikeTargetsActiveNotControlling() {
        Game g = startedGame(3, 1000);
        winFaceOff(g, "B");
        Resp steal = strikeUntilSteal(g);
        assertThat(activeTeam(steal)).isEqualTo("A");
        assertThat((String) steal.read("$.round.controllingTeam")).isEqualTo("B");
        Resp end = expectStatus(strike(g), 200);
        expectStrikesByTeam(end, 1, 3);
    }
}
