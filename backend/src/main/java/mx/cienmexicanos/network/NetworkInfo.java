package mx.cienmexicanos.network;

/** Esquema NetworkInfo de openapi.yaml. */
public record NetworkInfo(String lanAddress, int backendPort, int frontendPort) {}
