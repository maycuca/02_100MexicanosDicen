package mx.cienmexicanos.common.api;

/** Esquema CreateRoomResponse. */
public record CreateRoomResponse(String code, String presenterToken, String presenterClaimToken,
                                 PresenterRoomState state) {}
