package mx.cienmexicanos.acceptance;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Cliente STOMP mínimo para pruebas de caja negra (asyncapi.yaml). Los payloads se manejan como texto JSON
 * y se consultan con JsonPath; así la prueba no depende del Jackson del servidor.
 */
final class StompTestClient implements AutoCloseable {

    /** Mensaje recibido con su instante de llegada (para medir latencias, AC-08 / AC-14). */
    record Received(long atNanos, String payload) {}

    /** Buzón de una suscripción: acumula mensajes y permite esperar por condición. */
    static final class Inbox {
        private final List<Received> messages = new ArrayList<>();

        synchronized void add(String payload) {
            messages.add(new Received(System.nanoTime(), payload));
            notifyAll();
        }

        synchronized List<Received> all() {
            return List.copyOf(messages);
        }

        synchronized List<String> payloads() {
            return messages.stream().map(Received::payload).toList();
        }

        synchronized Optional<Received> first(Predicate<String> p) {
            return messages.stream().filter(m -> p.test(m.payload())).findFirst();
        }

        /** Espera hasta que llegue un mensaje que cumpla {@code p}; falla al vencer el plazo. */
        synchronized Received await(Predicate<String> p, Duration timeout) {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                Optional<Received> hit = first(p);
                if (hit.isPresent()) {
                    return hit.get();
                }
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    throw new AssertionError("no llegó el mensaje esperado en " + timeout + "; recibidos: " + payloads());
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
        }

        /** Número de mensajes recibidos hasta ahora (marca para {@link #awaitFrom}). */
        synchronized int size() {
            return messages.size();
        }

        /** Mensajes recibidos a partir de la marca {@code from} (índice de llegada). */
        synchronized List<String> payloadsFrom(int from) {
            return messages.subList(Math.min(from, messages.size()), messages.size()).stream().map(Received::payload).toList();
        }

        /** Como {@link #await} pero solo considera mensajes llegados a partir de la marca {@code from}. */
        synchronized Received awaitFrom(int from, Predicate<String> p, Duration timeout) {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                for (int i = from; i < messages.size(); i++) {
                    if (p.test(messages.get(i).payload())) {
                        return messages.get(i);
                    }
                }
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    throw new AssertionError("no llegó el mensaje esperado en " + timeout + "; recibidos desde la marca "
                            + from + ": " + payloadsFrom(from));
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
        }

        /** Como {@link #await} pero sin fallar: devuelve vacío si vence el plazo. */
        synchronized Optional<Received> tryAwait(Predicate<String> p, Duration timeout) {
            try {
                return Optional.of(await(p, timeout));
            } catch (AssertionError e) {
                return Optional.empty();
            }
        }
    }

    private final WebSocketStompClient client;
    private final StompSession session;
    /** Frames ERROR del servidor (p. ej. suscripción rechazada). */
    final Inbox errorFrames;
    /** Excepciones de transporte o de manejo de frames. */
    final List<Throwable> failures;

    private StompTestClient(WebSocketStompClient client, StompSession session, Inbox errorFrames, List<Throwable> failures) {
        this.client = client;
        this.session = session;
        this.errorFrames = errorFrames;
        this.failures = failures;
    }

    static StompTestClient connect(String url, Map<String, String> connectHeaders) {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new LenientStringConverter());
        StompHeaders headers = new StompHeaders();
        connectHeaders.forEach(headers::add);
        Inbox errors = new Inbox();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders h, Object payload) {
                errors.add(h.getFirst("message") + " " + payload);
            }

            @Override
            public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable ex) {
                failures.add(ex);
            }

            @Override
            public void handleTransportError(StompSession s, Throwable ex) {
                failures.add(ex);
            }
        };
        try {
            StompSession session = client.connectAsync(url, new WebSocketHttpHeaders(), headers, handler)
                    .get(5, TimeUnit.SECONDS);
            return new StompTestClient(client, session, errors, failures);
        } catch (Exception e) {
            client.stop();
            throw new AssertionError("no se pudo conectar por STOMP a " + url + " con " + connectHeaders.keySet(), e);
        }
    }

    /** Suscribe y devuelve el buzón donde se acumulan los payloads (texto JSON). */
    Inbox subscribe(String destination) {
        Inbox inbox = new Inbox();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add(payload == null ? "" : payload.toString());
            }
        });
        return inbox;
    }

    void send(String destination, String jsonBody) {
        StompHeaders h = new StompHeaders();
        h.setDestination(destination);
        h.setContentType(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
        session.send(h, jsonBody.getBytes(StandardCharsets.UTF_8));
    }

    boolean isConnected() {
        return session.isConnected();
    }

    @Override
    public void close() {
        try {
            if (session.isConnected()) {
                session.disconnect();
            }
        } catch (RuntimeException ignored) {
            // la sesión pudo cerrarse ya por el servidor
        } finally {
            client.stop();
        }
    }

    /** Convierte cualquier payload a String (el servidor envía application/json) y acepta byte[] al enviar. */
    private static final class LenientStringConverter extends StringMessageConverter {
        LenientStringConverter() {
            super(StandardCharsets.UTF_8);
        }

        @Override
        protected boolean supportsMimeType(MessageHeaders headers) {
            return true;
        }

        @Override
        protected boolean supports(Class<?> clazz) {
            return String.class == clazz || byte[].class == clazz;
        }

        @Override
        protected Object convertToInternal(Object payload, MessageHeaders headers, Object conversionHint) {
            return payload instanceof byte[] bytes ? bytes : super.convertToInternal(payload, headers, conversionHint);
        }
    }
}
