package mx.cienmexicanos.common.api;

/** Mensaje Error de asyncapi.yaml ({@code /user/queue/errors}). */
public record StompError(String code, String message) {}
