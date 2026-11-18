package mx.cienmexicanos.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomCodeGeneratorTest {

    @Test
    @DisplayName("AC-01 código de 4 letras mayúsculas sin I ni O")
    void format() {
        RoomCodeGenerator generator = new RoomCodeGenerator();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5_000; i++) {
            String code = generator.next();
            assertThat(code).matches("^[A-HJ-NP-Z]{4}$");
            seen.add(code);
        }
        assertThat(seen).hasSizeGreaterThan(4_000);
    }
}
