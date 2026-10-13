package ph.thecoffeejunkie.crm;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Full-context test against one shared Postgres and Redis for the whole JVM. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = IntegrationTest.Containers.class)
public @interface IntegrationTest {

    class Containers implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("crm")
                .withUsername("crm_owner")
                .withPassword("crm_owner")
                .withEnv("DB_APP_PASSWORD", "crm_app")
                .withCopyFileToContainer(MountableFile.forHostPath("deploy/postgres/init/01-roles.sh", 0755),
                        "/docker-entrypoint-initdb.d/01-roles.sh");

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            synchronized (POSTGRES) {
                if (!POSTGRES.isRunning()) {
                    POSTGRES.start();
                }
            }
            String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/crm";
            TestPropertyValues.of(
                    "spring.datasource.url=" + url,
                    "spring.datasource.username=crm_app",
                    "spring.datasource.password=crm_app",
                    "spring.flyway.user=crm_owner",
                    "spring.flyway.password=crm_owner",
                    "spring.data.redis.host=" + RedisTestSupport.host(),
                    "spring.data.redis.port=" + RedisTestSupport.port()
            ).applyTo(context);
        }
    }
}
