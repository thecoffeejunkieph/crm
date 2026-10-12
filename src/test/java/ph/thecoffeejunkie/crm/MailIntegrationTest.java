package ph.thecoffeejunkie.crm;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link IntegrationTest} with a mocked JavaMailSender. Every mail-capturing test class uses this one annotation:
 * each distinct set of {@code @MockitoBean}s would otherwise create its own cached Spring context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@IntegrationTest
@MockitoBean(types = JavaMailSender.class)
public @interface MailIntegrationTest {
}
