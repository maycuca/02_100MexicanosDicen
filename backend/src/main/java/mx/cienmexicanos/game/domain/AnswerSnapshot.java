package mx.cienmexicanos.game.domain;

/** Respuesta de una pregunta, copiada del banco al iniciar la partida. */
public record AnswerSnapshot(long id, String text, int points, int position) {}
