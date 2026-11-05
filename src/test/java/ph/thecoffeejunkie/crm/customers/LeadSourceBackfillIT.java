package ph.thecoffeejunkie.crm.customers;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ph.thecoffeejunkie.crm.IntegrationTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** V14 turns free-text customer sources into lead_source rows. Runs the migrations into a scratch schema. */
@IntegrationTest
class LeadSourceBackfillIT {

    private static final String SCHEMA = "backfill_t";

    @Value("${spring.datasource.url}") String url;

    private DriverManagerDataSource owner() {
        return new DriverManagerDataSource(url + "?currentSchema=" + SCHEMA, "crm_owner", "crm_owner");
    }

    @AfterEach
    void drop() {
        new JdbcTemplate(new DriverManagerDataSource(url, "crm_owner", "crm_owner"))
                .execute("drop schema if exists " + SCHEMA + " cascade");
    }

    private Flyway flyway(String target) {
        var c = Flyway.configure().dataSource(owner()).schemas(SCHEMA).defaultSchema(SCHEMA)
                .locations("classpath:db/migration");
        return (target == null ? c : c.target(target)).load();
    }

    @Test
    void freeTextSourcesBecomeRows() {
        flyway("13").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(owner());
        String[] sources = {"facebook", " Facebook ", "FB", "fb", "", null};
        for (int i = 0; i < sources.length; i++) {
            jdbc.update("insert into customer (type, display_name, payment_terms, source) values ('INDIVIDUAL', ?, "
                    + "'DUE_ON_RECEIPT', ?)", "C" + i, sources[i]);
        }

        flyway(null).migrate();

        Map<String, Long> ids = new java.util.HashMap<>();
        jdbc.query("select id, name from lead_source", rs -> {
            ids.put(rs.getString("name"), rs.getLong("id"));
        });
        assertThat(ids).hasSize(7).containsKeys("Walk-in", "Referral", "Facebook", "Website", "Phone call",
                "Trade show", "FB");
        assertThat(sourceOf(jdbc, "C0")).isEqualTo(ids.get("Facebook"));
        assertThat(sourceOf(jdbc, "C1")).isEqualTo(ids.get("Facebook"));
        assertThat(sourceOf(jdbc, "C2")).isEqualTo(ids.get("FB"));
        assertThat(sourceOf(jdbc, "C3")).isEqualTo(ids.get("FB"));
        assertThat(sourceOf(jdbc, "C4")).isNull();
        assertThat(sourceOf(jdbc, "C5")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = ? "
                + "and table_name = 'customer' and column_name = 'source'", Integer.class, SCHEMA)).isZero();
    }

    private static Long sourceOf(JdbcTemplate jdbc, String name) {
        return jdbc.queryForObject("select source_id from customer where display_name = ?", Long.class, name);
    }
}
