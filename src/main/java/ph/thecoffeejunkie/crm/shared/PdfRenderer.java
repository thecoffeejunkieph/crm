package ph.thecoffeejunkie.crm.shared;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders templates/pdf/{template}.html with the compiled Tailwind CSS inlined, then prints it with headless Chromium.
 * After changing a template's classes run {@code npm run build} in tailwind/.
 */
@Component
public class PdfRenderer {

    private final TemplateEngine templates = new TemplateEngine();
    private final String tailwind;
    private Playwright playwright;
    private Browser browser;

    public PdfRenderer() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/pdf/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        templates.setTemplateResolver(resolver);
        try {
            tailwind = new ClassPathResource("templates/pdf/tailwind.css").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] render(String template, Map<String, Object> vars) {
        var ctx = new Context();
        ctx.setVariables(vars);
        // th:text escapes values, so the only </head> is the template's own.
        return print(templates.process(template, ctx).replace("</head>", "<style>" + tailwind + "</style></head>"));
    }

    /** Template variables from key/value pairs; null values become "" so templates need no null checks. */
    public static Map<String, Object> vars(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            Object v = kv[i + 1];
            m.put((String) kv[i], v == null ? "" : v instanceof List<?> || v instanceof Map<?, ?> || v instanceof Number
                    ? v : v.toString());
        }
        return m;
    }

    // ponytail: one browser behind one lock (Playwright objects are single-threaded); pool browsers if PDFs start queueing.
    private synchronized byte[] print(String html) {
        if (browser == null || !browser.isConnected()) {
            launch();
        }
        // Documents hold customer data and need nothing external: no scripts, no network.
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false));
             Page page = context.newPage()) {
            page.route("**/*", route -> route.abort());
            page.setContent(html);
            return page.pdf(new Page.PdfOptions().setPreferCSSPageSize(true).setPrintBackground(true));
        }
    }

    private void launch() {
        close();
        // Never download browsers at runtime: the Docker image ships Chromium, dev and CI install it once.
        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setArgs(List.of("--disable-dev-shm-usage")));
    }

    @PreDestroy
    public synchronized void close() {
        if (playwright != null) {
            playwright.close();
        }
        playwright = null;
        browser = null;
    }
}
