package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.files.StorageService;
import ph.thecoffeejunkie.crm.shared.Role;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class PaymentsIT {

    static final byte[] PDF = "%PDF-1.7 proof".getBytes();

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    private ResultActions putProof(long paymentId, MockMultipartFile file, RequestPostProcessor user) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/payments/" + paymentId + "/proof")
                .file(file).with(csrf(mvc)).with(user));
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "p.png", "image/png", PNG);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StorageService storage;

    SalesFixture f;
    long rep;
    long customer;
    long product;
    long order;
    RequestPostProcessor asRep;

    @BeforeEach
    void seed() throws Exception {
        f = new SalesFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        product = f.product("A", "112.00", null, "VATABLE");
        asRep = as(rep, Role.SALES);
        order = f.createdOrder(asRep, customer, line(product, "3")); // total 336.00
        f.setStatus(order, "CONFIRMED");
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    static String payment(String cash, String withholding) {
        return "{\"method\":\"BANK_TRANSFER\",\"cashAmount\":" + cash + ",\"withholdingAmount\":" + withholding
                + ",\"receivedOn\":\"" + LocalDate.now(ZoneId.of("Asia/Manila")) + "\",\"reference\":\"REF-1\"}";
    }

    private ResultActions record(long orderId, String json, MockMultipartFile proof) throws Exception {
        var req = multipart("/api/orders/" + orderId + "/payments")
                .file(new MockMultipartFile("payment", "", "application/json", json.getBytes()));
        if (proof != null) {
            req.file(proof);
        }
        return mvc.perform(req.header("Idempotency-Key", UUID.randomUUID().toString()).with(csrf(mvc)).with(asRep));
    }

    private ResultActions orderJson() throws Exception {
        return mvc.perform(get("/api/orders/" + order).with(asRep));
    }

    private long pendingPayment(String cash) {
        return jdbc.queryForObject("insert into payment (order_id, status, method, cash_amount, received_on, "
                + "submitted_by_type, submitted_by_id) values (?, 'PENDING', 'GCASH', ?::numeric, current_date, 'USER', ?) "
                + "returning id", Long.class, order, cash, rep);
    }

    private ResultActions verify(long paymentId) throws Exception {
        long version = jdbc.queryForObject("select version from payment where id = ?", Long.class, paymentId);
        return mvc.perform(f.json(post("/api/payments/" + paymentId + "/verify"), "{\"version\":" + version + "}")
                .with(asRep));
    }

    @Test
    void recordReducesBalance() throws Exception {
        record(order, payment("300", "0"), new MockMultipartFile("proof", "p.pdf", "application/pdf", PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.proofUrl").isNotEmpty())
                .andExpect(jsonPath("$.submittedByType").value("USER"));
        orderJson().andExpect(jsonPath("$.balance").value(36.00))
                .andExpect(jsonPath("$.settled").value(300.00))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIAL"));
    }

    @Test
    void withholdingSettles() throws Exception {
        record(order, payment("332.64", "3.36"), null).andExpect(status().isOk());
        orderJson().andExpect(jsonPath("$.balance").value(0)).andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test
    void overpaymentRejected() throws Exception {
        record(order, payment("300", "0"), null).andExpect(status().isOk());
        record(order, payment("40", "0"), null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OVERPAYMENT"));
        assertThat(jdbc.queryForObject("select settled_amount from sales_order where id = ?", String.class, order))
                .isEqualTo("300.00");
    }

    @Test
    void racingPaymentsOneOverpays() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                gate.await();
                return record(order, payment("200", "0"), null).andReturn().getResponse().getStatus();
            }));
        }
        gate.countDown();
        List<Integer> codes = new ArrayList<>();
        for (var r : results) {
            codes.add(r.get());
        }
        pool.shutdown();
        assertThat(codes).containsExactlyInAnyOrder(200, 422);
        assertThat(jdbc.queryForObject("select settled_amount from sales_order where id = ?", String.class, order))
                .isEqualTo("200.00");
    }

    @Test
    void pendingCountsOnlyAfterVerify() throws Exception {
        long p = pendingPayment("100");
        orderJson().andExpect(jsonPath("$.balance").value(336.00));
        verify(p).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFIED"));
        orderJson().andExpect(jsonPath("$.balance").value(236.00));
    }

    @Test
    void rejectNeedsReason() throws Exception {
        long p = pendingPayment("100");
        mvc.perform(f.json(post("/api/payments/" + p + "/reject"), "{\"version\":0,\"reason\":\" \"}").with(asRep))
                .andExpect(status().isBadRequest());
        mvc.perform(f.json(post("/api/payments/" + p + "/reject"), "{\"version\":0,\"reason\":\"No such transfer\"}")
                .with(asRep)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("No such transfer"));
    }

    @Test
    void verifyTwice422() throws Exception {
        long p = pendingPayment("100");
        verify(p).andExpect(status().isOk());
        verify(p).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void draftOrderCannotTakePayment() throws Exception {
        long draft = f.createdOrder(asRep, customer, line(product, "1"));
        record(draft, payment("10", "0"), null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void attach2307ShowsUrl() throws Exception {
        long p = SalesFixture.id(record(order, payment("300", "3"), null));
        mvc.perform(multipart(HttpMethod.PUT, "/api/payments/" + p + "/form-2307")
                        .file(new MockMultipartFile("file", "2307.pdf", "application/pdf", PDF))
                        .with(csrf(mvc)).with(asRep))
                .andExpect(status().isOk()).andExpect(jsonPath("$.form2307Url").isNotEmpty());
        mvc.perform(get("/api/orders/" + order + "/payments").with(asRep))
                .andExpect(jsonPath("$[0].form2307Url").isNotEmpty());
    }

    @Test
    void badProofType400() throws Exception {
        record(order, payment("10", "0"), new MockMultipartFile("proof", "p.txt", "text/plain", "hello".getBytes()))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from payment where order_id = ?", Integer.class, order)).isZero();
    }

    @Test
    void suggestedWithholdingOnOrder() throws Exception {
        orderJson().andExpect(jsonPath("$.suggestedWithholding").value(3.00));
        jdbc.update("update customer set type = 'INDIVIDUAL' where id = ?", customer);
        orderJson().andExpect(jsonPath("$.suggestedWithholding").value(0.00));
    }

    @Test
    void suggestedWithholdingNetsWhatWasWithheldAndCapsAtBalance() throws Exception {
        record(order, payment("100", "2"), null).andExpect(status().isOk());
        orderJson().andExpect(jsonPath("$.suggestedWithholding").value(1.00));
        record(order, payment("233.50", "0"), null).andExpect(status().isOk());
        orderJson().andExpect(jsonPath("$.suggestedWithholding").value(0.50));
    }

    @Test
    void attachProofToRecordedPayment() throws Exception {
        long p = SalesFixture.id(record(order, payment("300", "0"), null));
        putProof(p, png(), asRep).andExpect(status().isOk()).andExpect(jsonPath("$.proofUrl").isNotEmpty());
    }

    @Test
    void attachProofReplacesEarlier() throws Exception {
        long p = SalesFixture.id(record(order, payment("300", "0"), null));
        putProof(p, png(), asRep).andExpect(status().isOk());
        String first = jdbc.queryForObject("select proof_key from payment where id = ?", String.class, p);
        putProof(p, png(), asRep).andExpect(status().isOk());
        String second = jdbc.queryForObject("select proof_key from payment where id = ?", String.class, p);
        assertThat(second).isNotEqualTo(first);
        var http = HttpClient.newHttpClient();
        var gone = http.send(HttpRequest.newBuilder(URI.create(storage.presignedUrl(first))).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(gone.statusCode()).isEqualTo(404);
    }

    @Test
    void attachProofRejectsTextFile() throws Exception {
        long p = SalesFixture.id(record(order, payment("300", "0"), null));
        putProof(p, new MockMultipartFile("file", "p.txt", "text/plain", "hello".getBytes()), asRep)
                .andExpect(status().isBadRequest());
    }

    @Test
    void attachProofToRejectedPaymentRefused() throws Exception {
        long p = pendingPayment("100");
        jdbc.update("update payment set status = 'REJECTED', reject_reason = 'x' where id = ?", p);
        putProof(p, png(), asRep).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void attachProofOutOfScope() throws Exception {
        long p = SalesFixture.id(record(order, payment("300", "0"), null));
        putProof(p, png(), as(f.user("other", Role.SALES), Role.SALES)).andExpect(status().isNotFound());
    }
}
