package ph.thecoffeejunkie.crm.identity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SessionTransportIT {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("delete from app_user where email like '%@transport.it'");
    }

    @Test
    void androidLoginReturnsTokenAndHeaderWorksWithoutCsrf() throws Exception {
        var u = new AppUser();
        u.email = "droid@transport.it";
        u.passwordHash = new BCryptPasswordEncoder().encode("correct-horse-battery");
        u.firstName = "Dan";
        u.lastName = "Droid";
        u.role = Role.WAREHOUSE;
        users.save(u);

        var res = mvc.perform(post("/api/auth/login").header("X-Client-Type", "ANDROID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"droid@transport.it\",\"password\":\"correct-horse-battery\","
                                + "\"clientType\":\"ANDROID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next").value("DONE"))
                .andReturn().getResponse();
        String token = JsonPath.read(res.getContentAsString(), "$.sessionToken");
        assertThat(token).isNotBlank();
        assertThat(res.getHeader("X-Auth-Token")).isEqualTo(token);
        assertThat(res.getCookie("TCJSESSION")).isNull();

        mvc.perform(get("/api/me").header("X-Auth-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("droid@transport.it"));
        mvc.perform(put("/api/me").header("X-Auth-Token", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Danny\",\"lastName\":\"Droid\",\"phone\":\"0917\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Danny"))
                .andExpect(jsonPath("$.phone").value("0917"));
    }
}
