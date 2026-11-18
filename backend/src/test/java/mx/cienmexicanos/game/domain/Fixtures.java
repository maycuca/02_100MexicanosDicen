package mx.cienmexicanos.game.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Datos de prueba del dominio: preguntas con respuestas de 40/30/20/10 puntos. */
final class Fixtures {

    static final Instant T0 = Instant.parse("2026-11-17T10:00:00Z");

    private Fixtures() {}

    static QuestionSnapshot question(long id) {
        List<AnswerSnapshot> answers = new ArrayList<>();
        int[] points = {40, 30, 20, 10};
        for (int pos = 1; pos <= points.length; pos++) {
            answers.add(new AnswerSnapshot(answerId(id, pos), "R" + id + "-" + pos, points[pos - 1], pos));
        }
        return new QuestionSnapshot(id, "Pregunta " + id, answers);
    }

    static long answerId(long questionId, int position) {
        return questionId * 100 + position;
    }

    static List<QuestionSnapshot> questions(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(Fixtures::question).toList();
    }

    static Room room(int rounds, int target) {
        return new Room("KDMZ", "Equipo Rojo", "Equipo Verde", rounds, target, T0);
    }

    /** Sala con dos jugadores por equipo, aún en LOBBY. */
    static Room lobby(int rounds, int target) {
        Room room = room(rounds, target);
        room.addPlayer("Ana", TeamId.A);
        room.addPlayer("Beto", TeamId.B);
        room.addPlayer("Carla", TeamId.A);
        room.addPlayer("Dani", TeamId.B);
        room.drainEvents();
        return room;
    }

    static Room faceOff() {
        Room room = lobby(5, 300);
        room.start(questions(5));
        return room;
    }

    static Room playing(TeamId control) {
        Room room = faceOff();
        room.assignControl(control, Fixtures.T0);
        return room;
    }

    static Room steal() {
        Room room = playing(TeamId.A);
        room.strike(Fixtures.T0);
        room.strike(Fixtures.T0);
        room.strike(Fixtures.T0);
        return room;
    }

    static Room roundEnd() {
        Room room = steal();
        room.strike(Fixtures.T0);
        return room;
    }

    static Room gameOver() {
        Room room = lobby(3, 1000);
        room.start(questions(3));
        for (int r = 1; r <= 3; r++) {
            room.assignControl(TeamId.A, Fixtures.T0);
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            room.nextRound();
        }
        return room;
    }

    static long currentAnswer(Room room, int position) {
        return room.currentRound().orElseThrow().slots().get(position - 1).answerId();
    }
}
