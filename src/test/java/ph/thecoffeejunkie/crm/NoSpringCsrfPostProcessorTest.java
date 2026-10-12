package ph.thecoffeejunkie.crm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards against Spring Security's csrf() post-processor; use {@link CsrfSupport} instead. */
class NoSpringCsrfPostProcessorTest {

    @Test
    void noTestUsesSpringSecurityCsrfPostProcessor() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/test"))) {
            var offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("NoSpringCsrfPostProcessorTest.java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("SecurityMockMvcRequestPostProcessors.csrf");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }).toList();
            assertThat(offenders).isEmpty();
        }
    }
}
