package ph.thecoffeejunkie.crm.purchasing;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;

@IntegrationTest
class PurchaseOrderDocumentIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext ctx;

    PurchasingFixture f;
    RequestPostProcessor admin;
    long wh;
    long supplier;

    @BeforeEach
    void seed() {
        f = new PurchasingFixture(jdbc, tx, ctx);
        admin = as(f.inv.admin("adm"), Role.ADMIN);
        wh = f.inv.warehouse("A");
        supplier = f.supplier("S1", null);
        jdbc.update("update supplier set downpayment_percent = 50, payment_terms_days = 30 where id = ?", supplier);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    /** A DRAFT PO with the given number of lines, 10 x 10.00 each. */
    private long draft(int lines) throws Exception {
        List<String> ls = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            long p = f.inv.product("P" + i, "PC");
            ls.add("{\"productId\":" + p + ",\"qty\":10,\"unitCost\":10.00}");
        }
        String json = "{\"supplierId\":" + supplier + ",\"warehouseId\":" + wh + ",\"notes\":\"Deliver mornings\","
                + "\"lines\":[" + String.join(",", ls) + "]}";
        String res = mvc.perform(post("/api/purchase-orders").contentType("application/json").content(json)
                .with(csrf(mvc)).with(admin)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private byte[] body(String url) throws Exception {
        return mvc.perform(get(url).with(admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private String number(long id) {
        return jdbc.queryForObject("select number from purchase_order where id = ?", String.class, id);
    }

    @Test
    void pdfStartsWithMagic() throws Exception {
        long id = draft(1);
        mvc.perform(get("/api/purchase-orders/" + id + ".pdf").with(admin)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", startsWith("inline")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(number(id) + ".pdf")));
        assertThat(new String(body("/api/purchase-orders/" + id + ".pdf"), 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void pdfContainsNumberSupplierAndTerms() throws Exception {
        long id = draft(1);
        byte[] pdf = body("/api/purchase-orders/" + id + ".pdf");
        String text;
        try (var doc = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(doc);
        }
        assertThat(text).contains(number(id), "Supplier S1", "DRAFT");
        assertThat(text.replaceAll("\s+", " ")).contains("50% downpayment PHP 50.00 (paid PHP 0.00) · balance on 30-day credit");
        sample("sample-po.pdf", pdf);
    }

    @Test
    void pngIsFullPage() throws Exception {
        long id = draft(40);
        byte[] png = body("/api/purchase-orders/" + id + ".png");
        assertThat(png).startsWith(0x89, 'P', 'N', 'G');
        assertThat(ImageIO.read(new ByteArrayInputStream(png)).getHeight()).isGreaterThan(1123);
        sample("sample-po.png", png);
        sample("sample-po-40.pdf", body("/api/purchase-orders/" + id + ".pdf"));
    }

    @Test
    void downloadSetsAttachment() throws Exception {
        long id = draft(1);
        mvc.perform(get("/api/purchase-orders/" + id + ".png?download=true").with(admin)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", startsWith("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(number(id) + ".png")));
        mvc.perform(get("/api/purchase-orders/" + id + ".pdf?download=true").with(admin)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment")));
    }

    @Test
    void salesCannotRead() throws Exception {
        long id = draft(1);
        long otherWh = f.inv.warehouse("B");
        RequestPostProcessor sales = as(f.inv.user("rep"), Role.SALES);
        RequestPostProcessor other = as(f.inv.warehouseUser("whB", otherWh), Role.WAREHOUSE, otherWh);
        RequestPostProcessor own = as(f.inv.warehouseUser("whA", wh), Role.WAREHOUSE, wh);
        mvc.perform(get("/api/purchase-orders/" + id + ".pdf").with(sales)).andExpect(status().isForbidden());
        mvc.perform(get("/api/purchase-orders/" + id + ".pdf").with(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/purchase-orders/" + id + ".png").with(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/purchase-orders/" + id + ".pdf").with(own)).andExpect(status().isOk());
    }

    /** Writes a sample for eyeballing when PO_SAMPLES=<dir> is set. */
    private static void sample(String name, byte[] bytes) throws Exception {
        String dir = System.getenv("PO_SAMPLES");
        if (dir != null) {
            Files.write(Path.of(dir, name), bytes);
        }
    }
}
