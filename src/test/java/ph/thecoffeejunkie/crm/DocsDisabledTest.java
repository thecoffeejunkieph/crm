package ph.thecoffeejunkie.crm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The base config (prod/staging) turns springdoc off; simulate it on top of the test profile. */
@IntegrationTest
@TestPropertySource(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class DocsDisabledTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apiDocsAreNotServed() throws Exception {
        for (String path : new String[] {"/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui.html"}) {
            var res = mvc.perform(get(path)).andReturn().getResponse();
            assertNotEquals(200, res.getStatus(), path);
            assertFalse(res.getContentAsString().contains("openapi"), path);
        }
    }
}
