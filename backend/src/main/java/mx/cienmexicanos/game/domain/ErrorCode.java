package mx.cienmexicanos.game.domain;

/** Códigos de error estables (propiedad {@code code} del esquema Problem y del mensaje Error STOMP). */
public enum ErrorCode {
    ROOM_NOT_FOUND,
    ANSWER_NOT_FOUND,
    PLAYER_NOT_FOUND,
    CLAIM_USED,
    CLAIM_INVALID,
    NAME_TAKEN,
    ROOM_NOT_IN_LOBBY,
    INVALID_STATE,
    TEAM_EMPTY,
    NOT_YOUR_TURN,
    /** Spec 002, AC-40: X sin equipo en turno. */
    NO_ACTIVE_TEAM,
    /** Spec 003, AC-64: solo el capitán puede nombrar a su equipo. */
    NOT_CAPTAIN,
    /** Spec 003, AC-64: nombre igual al del otro equipo. */
    TEAM_NAME_TAKEN,
    NOT_ENOUGH_QUESTIONS,
    /** Spec 005, AC-81: la sala no tiene temporizador. */
    TIMER_DISABLED,
    /** Spec 008, AC-122: ya no quedan preguntas para cambiar. */
    NO_MORE_QUESTIONS,
    VALIDATION_ERROR,
    UNAUTHORIZED,
    FORBIDDEN
}
