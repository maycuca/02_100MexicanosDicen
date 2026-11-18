package mx.cienmexicanos.game.domain;

/** Tipos de GameEvent de asyncapi.yaml. */
public enum GameEventType {
    PLAYER_JOINED,
    PRESENTER_CLAIMED,
    PLAYER_LEFT,
    GAME_STARTED,
    BUZZ_WON,
    ANSWER_REVEALED,
    STRIKE,
    CONTROL_ASSIGNED,
    STEAL_STARTED,
    ROUND_ENDED,
    GAME_OVER,
    /** Spec 002, AC-36. */
    TURN_CHANGED,
    /** Spec 002, AC-48. */
    TIMER_EXPIRED,
    /** Spec 002, AC-53. */
    SOUND_TOGGLED,
    /** Spec 003, AC-65. */
    TEAM_RENAMED,
    /** Spec 005, AC-81. */
    TIMER_CHANGED,
    /** Spec 005, AC-85: fallo en el cara a cara, sin X. */
    FACE_OFF_MISS,
    /** Spec 005, AC-91. */
    ROOM_CLOSED,
    /** Spec 008, AC-122. */
    QUESTION_CHANGED
}
