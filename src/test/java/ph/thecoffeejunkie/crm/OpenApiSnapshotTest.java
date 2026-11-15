package ph.thecoffeejunkie.crm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class OpenApiSnapshotTest {

    private static final Path SNAPSHOT = Path.of("openapi.json");

    // Map keys sorted so springdoc's ordering can never make the snapshot flap.
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT, SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Autowired
    MockMvc mvc;

    @Test
    void apiDocsMatchCommittedSnapshot() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String actual = MAPPER.writeValueAsString(MAPPER.readValue(body, Map.class)).replace("\r\n", "\n") + "\n";

        if (Boolean.parseBoolean(System.getProperty("updateSnapshot"))) {
            Files.writeString(SNAPSHOT, actual, StandardCharsets.UTF_8);
            return;
        }
        String expected = Files.exists(SNAPSHOT)
                ? Files.readString(SNAPSHOT, StandardCharsets.UTF_8).replace("\r\n", "\n") : "";
        assertEquals(expected, actual, "run ./gradlew test --tests '*OpenApiSnapshotTest' -DupdateSnapshot=true");
    }
}
