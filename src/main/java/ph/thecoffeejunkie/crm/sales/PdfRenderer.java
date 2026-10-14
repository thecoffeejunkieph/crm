package ph.thecoffeejunkie.crm.sales;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Renders templates/pdf/{template}.html (well-formed XHTML) to PDF. Base-14 fonts only, hence "PHP" not the peso sign. */
@Component
class PdfRenderer {

    private final TemplateEngine templates = new TemplateEngine();

    PdfRenderer() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/pdf/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        templates.setTemplateResolver(resolver);
    }

    byte[] render(String template, Map<String, Object> vars) {
        var ctx = new Context();
        ctx.setVariables(vars);
        String html = templates.process(template, ctx);
        try (var out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
