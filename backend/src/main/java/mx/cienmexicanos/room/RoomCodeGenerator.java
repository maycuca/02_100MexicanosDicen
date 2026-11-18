package mx.cienmexicanos.room;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/** AC-01: códigos de 4 letras mayúsculas sin I ni O ({@code ^[A-HJ-NP-Z]{4}$}). */
@Component
public class RoomCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final int LENGTH = 4;

    private final RandomGenerator random;

    public RoomCodeGenerator() {
        this(new SecureRandom());
    }

    RoomCodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    public String next() {
        var sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
