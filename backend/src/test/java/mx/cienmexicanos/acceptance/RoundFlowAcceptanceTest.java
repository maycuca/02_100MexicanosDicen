package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * US-4…US-6 Cara a cara, ronda y robo — revealAnswer, markStrike, assignControl (AC-15…AC-20).
 * Los ids y puntos de las respuestas se leen de presenter-view porque la pregunta es aleatoria (AC-11).
 */
@Tag("acceptance")
@DisplayName("Aceptación · Flujo de ronda")
class RoundFlowAcceptanceTest extends AcceptanceTestBase {

    @Test
    @DisplayName("spec 006 AC-92 AC-99 cara a cara: el buzzer da la respuesta #1 y su equipo recibe el control (PLAYING) sin asignar")
    void faceOffTopAnswerGivesControl() {
        Game g = startedGame(3, 1000);
        Slot top = slotAt(g, 1);

        buzzBy(g, "A");
        Resp r = expectStatus(reveal(g, top.answerId()), 200);
        assertThat(r.intAt("$.round.pot")).as("AC-17 el bote incluye lo revelado en el cara a cara").isEqualTo(top.points());
        assertThat(presenterBoard(r)).filteredOn(Slot::revealed).extracting(Slot::position).containsExactly(1);
        assertThat(r.roomStatus()).isEqualTo("PLAYING");
        assertThat((String) r.read("$.round.controllingTeam")).isEqualTo("A");
        assertThat(r.intAt("$.round.strikes")).isZero();
        assertThat((Object) r.readOrNull("$.round.potWinner")).as("potWinner solo en ROUND_END").isNull();
    }

    @Test
    @DisplayName("spec 006 AC-95 AC-96 cara a cara: el rival con una respuesta más alta gana el control; con una más baja, el primero")
    void faceOffRivalWithHigherAnswerGetsControl() {
        Game g = startedGame(3, 1000);
        Slot second = slotAt(g, 2);
        Slot top = slotAt(g, 1);

        buzzBy(g, "A");
        Resp r1 = expectStatus(reveal(g, second.answerId()), 200);
        assertThat(r1.roomStatus()).isEqualTo("FACE_OFF");
        Resp r = expectStatus(reveal(g, top.answerId()), 200);
        assertThat(r.intAt("$.round.pot")).isEqualTo(second.points() + top.points());
        assertThat(r.roomStatus()).isEqualTo("PLAYING");
        assertThat((String) r.read("$.round.controllingTeam")).isEqualTo("B");

        Game lower = startedGame(3, 1000);
        buzzBy(lower, "A");
        expectStatus(reveal(lower, slotAt(lower, 2).answerId()), 200);
        Resp l = expectStatus(reveal(lower, slotAt(lower, 3).answerId()), 200);
        assertThat(l.roomStatus()).isEqualTo("PLAYING");
        assertThat((String) l.read("$.round.controllingTeam")).as("AC-96 la #3 no supera la #2").isEqualTo("A");
    }

    @Test
    @DisplayName("spec 006 AC-100 en el cara a cara no se revela sin buzzer → 409 NO_ACTIVE_TEAM")
    void revealWithoutBuzzRejected() {
        Game g = startedGame(3, 1000);
        expectProblem(reveal(g, slotAt(g, 1).answerId()), 409, "NO_ACTIVE_TEAM");
        assertThat(presenterBoard(presenterView(g))).noneMatch(Slot::revealed);
    }

    @Test
    @DisplayName("AC-15 cara a cara: una X para el buzzer mantiene FACE_OFF")
    void faceOffStrikeStaysInFaceOff() {
        Game g = startedGame(3, 1000);
        buzzBy(g, "B");
        Resp r = expectStatus(strike(g), 200);
        assertThat(r.roomStatus()).isEqualTo("FACE_OFF");
    }

    @Test
    @DisplayName("AC-15 assignControl con equipo inválido → 400")
    void assignControlInvalidTeam() {
        Game g = startedGame(3, 1000);
        buzzBy(g, "A");
        Resp r = post("/api/rooms/" + g.code() + "/rounds/current/control", "{\"team\":\"C\"}", presenter(g.token()));
        assertThat(r.status()).as("respuesta: %s", r).isEqualTo(400);
    }

    @Test
    @DisplayName("AC-16 AC-17 en PLAYING cada respuesta revelada suma al bote, no al marcador")
    void revealsAccumulateInPotNotScore() {
        Game g = startedGame(3, 1000);
        Slot top = slotAt(g, 1);
        Slot second = slotAt(g, 2);
        winFaceOff(g, "A");

        Resp r = expectStatus(reveal(g, second.answerId()), 200);
        assertThat(r.roomStatus()).isEqualTo("PLAYING");
        assertThat(r.intAt("$.round.pot")).isEqualTo(top.points() + second.points());
        assertThat(scoreOf(r, "A")).isZero();
        assertThat(scoreOf(r, "B")).isZero();

        Resp pub = expectStatus(getRoom(g.code()), 200);
        assertThat(pub.intAt("$.round.pot")).isEqualTo(top.points() + second.points());
        assertThat(scoreOf(pub, "A")).isZero();
    }

    @Test
    @DisplayName("AC-16 en PLAYING un strike incrementa el contador sin cambiar el bote")
    void strikeIncrementsCounter() {
        Game g = startedGame(3, 1000);
        Resp control = winFaceOff(g, "A");
        int pot = control.intAt("$.round.pot");

        Resp s1 = expectStatus(strike(g), 200);
        assertThat(s1.roomStatus()).isEqualTo("PLAYING");
        assertThat(s1.intAt("$.round.strikes")).isEqualTo(1);
        assertThat(s1.intAt("$.round.pot")).isEqualTo(pot);
        Resp s2 = expectStatus(strike(g), 200);
        assertThat(s2.roomStatus()).isEqualTo("PLAYING");
        assertThat(s2.intAt("$.round.strikes")).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-18 con 3 strikes la sala pasa a STEAL")
    void threeStrikesMoveToSteal() {
        Game g = startedGame(3, 1000);
        winFaceOff(g, "A");
        expectStatus(strike(g), 200);
        expectStatus(strike(g), 200);
        Resp s3 = expectStatus(strike(g), 200);
        assertThat(s3.roomStatus()).isEqualTo("STEAL");
        assertThat(s3.intAt("$.round.strikes")).isEqualTo(3);
        assertThat((String) s3.read("$.round.controllingTeam")).isEqualTo("A");
    }

    @Test
    @DisplayName("AC-18 AC-20 si se revelan todas, la ronda termina y el bote ×1 es del equipo con control")
    void revealAllEndsRoundForControllingTeam() {
        Game g = startedGame(3, 1000);
        List<Slot> board = board(g);
        winFaceOff(g, "A");
        Resp last = null;
        for (Slot s : board.subList(1, board.size())) {
            last = expectStatus(reveal(g, s.answerId()), 200);
        }
        int total = board.stream().mapToInt(Slot::points).sum();

        assertThat(last.roomStatus()).isEqualTo("ROUND_END");
        assertThat(last.intAt("$.round.multiplier")).isEqualTo(1);
        assertThat((String) last.read("$.round.potWinner")).isEqualTo("A");
        assertThat(scoreOf(last, "A")).isEqualTo(total);
        assertThat(scoreOf(last, "B")).isZero();
    }

    @Test
    @DisplayName("AC-19 robo acertado: el bote es del equipo rival")
    void successfulStealGivesPotToRival() {
        Game g = startedGame(3, 1000);
        Slot second = slotAt(g, 2);
        winFaceOff(g, "A");
        Resp steal = strikeUntilSteal(g);
        int pot = steal.intAt("$.round.pot");

        Resp end = expectStatus(reveal(g, second.answerId()), 200);
        assertThat(end.roomStatus()).isEqualTo("ROUND_END");
        // data-model.md: el bote solo crece en PLAYING/FACE_OFF; en STEAL el acierto transfiere el bote existente.
        assertThat((String) end.read("$.round.potWinner")).isEqualTo("B");
        assertThat(scoreOf(end, "B")).isEqualTo(pot);
        assertThat(scoreOf(end, "A")).isZero();
    }

    @Test
    @DisplayName("AC-19 robo fallido: el bote es del equipo con control")
    void failedStealKeepsPotForControllingTeam() {
        Game g = startedGame(3, 1000);
        winFaceOff(g, "B");
        int pot = strikeUntilSteal(g).intAt("$.round.pot");

        Resp end = expectStatus(strike(g), 200);
        assertThat(end.roomStatus()).isEqualTo("ROUND_END");
        assertThat((String) end.read("$.round.potWinner")).isEqualTo("B");
        assertThat(scoreOf(end, "B")).isEqualTo(pot);
        assertThat(scoreOf(end, "A")).isZero();
    }

    @Test
    @DisplayName("AC-20 en ROUND_END el tablero público muestra todas las respuestas con texto y puntos")
    void roundEndRevealsWholeBoardPublicly() {
        Game g = startedGame(3, 1000);
        List<Slot> board = board(g);
        playRoundKeepingPot(g, "A");

        Resp pub = expectStatus(getRoom(g.code()), 200);
        assertThat((String) pub.read("$.status")).isEqualTo("ROUND_END");
        assertThat((String) pub.read("$.round.potWinner")).isEqualTo("A");
        assertThat(pub.<Boolean>list("$.round.board[*].revealed")).hasSize(board.size()).containsOnly(true);
        assertThat(pub.<String>list("$.round.board[*].text"))
                .containsExactlyInAnyOrderElementsOf(board.stream().map(Slot::text).toList());
        assertThat(pub.<Integer>list("$.round.board[*].points"))
                .containsExactlyInAnyOrderElementsOf(board.stream().map(Slot::points).toList());
    }

    @Test
    @DisplayName("AC-15 AC-16 AC-17 AC-18 AC-19 AC-20 ronda completa: cara a cara → juego → robo → cierre")
    void fullRound() {
        Game g = startedGame(3, 1000);
        List<Slot> board = board(g);
        Slot top = board.get(0);
        Slot second = board.get(1);
        Slot third = board.get(2);

        Resp pv0 = presenterView(g);
        assertThat(pv0.roomStatus()).isEqualTo("FACE_OFF");
        buzzBy(g, "A");
        expectStatus(reveal(g, third.answerId()), 200);           // buzzer de A: respuesta #3
        Resp control = expectStatus(reveal(g, top.answerId()), 200); // B: #1 → B gana el control (spec 006 AC-95)
        assertThat((String) control.read("$.round.controllingTeam")).isEqualTo("B");
        assertThat(control.intAt("$.round.pot")).isEqualTo(top.points() + third.points());

        Resp played = expectStatus(reveal(g, second.answerId()), 200);
        int pot = top.points() + second.points() + third.points();
        assertThat(played.intAt("$.round.pot")).isEqualTo(pot);
        Resp steal = strikeUntilSteal(g);
        assertThat(steal.intAt("$.round.pot")).isEqualTo(pot);

        Resp end = expectStatus(strike(g), 200);                  // A falla el robo
        assertThat(end.roomStatus()).isEqualTo("ROUND_END");
        assertThat((String) end.read("$.round.potWinner")).isEqualTo("B");
        assertThat(scoreOf(end, "B")).isEqualTo(pot * end.intAt("$.round.multiplier"));
        assertThat(scoreOf(end, "A")).isZero();
        assertThat(presenterBoard(end)).allMatch(Slot::revealed);
    }

    @Test
    @DisplayName("revelar una respuesta inexistente → 404")
    void revealUnknownAnswer() {
        Game g = startedGame(3, 1000);
        buzzBy(g, "A");
        long maxId = board(g).stream().mapToLong(Slot::answerId).max().orElseThrow();
        Resp r = reveal(g, maxId + 100_000);
        assertThat(r.status()).as("respuesta: %s", r).isEqualTo(404);
    }
}
