package ph.thecoffeejunkie.crm.attachments;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ph.thecoffeejunkie.crm.attachments.AttachmentsFixture.scope;
import static ph.thecoffeejunkie.crm.attachments.SubjectType.CUSTOMER;
import static ph.thecoffeejunkie.crm.attachments.SubjectType.INVOICE;
import static ph.thecoffeejunkie.crm.attachments.SubjectType.ORDER;

@IntegrationTest
class AttachmentAccessIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired AttachmentAccess access;

    AttachmentsFixture f;
    Scope admin;
    Scope rep;
    Scope other;
    long acme;
    long order;
    long invoice;
    long whA;
    long whB;

    @BeforeEach
    void seed() {
        f = new AttachmentsFixture(jdbc);
        admin = scope(f.user("adm", Role.ADMIN), Role.ADMIN);
        long repId = f.user("rep", Role.SALES);
        rep = scope(repId, Role.SALES);
        other = scope(f.user("oth", Role.SALES), Role.SALES);
        acme = f.customer("acme", repId);
        order = f.order(acme, "CONFIRMED");
        invoice = f.invoice(order, "ISSUED");
        whA = f.warehouse("A");
        whB = f.warehouse("B");
        f.delivery(order, whA);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private static void readOnly(Runnable r) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.ATTACHMENT_READ_ONLY));
    }

    @Test
    void repSeesOwnRecordsOnly() {
        assertThat(access.canSee(rep, CUSTOMER, acme)).isTrue();
        assertThat(access.canSee(rep, ORDER, order)).isTrue();
        assertThat(access.canSee(rep, INVOICE, invoice)).isTrue();
        assertThat(access.canSee(other, CUSTOMER, acme)).isFalse();
        assertThat(access.canSee(other, ORDER, order)).isFalse();
        assertThat(access.canSee(other, INVOICE, invoice)).isFalse();
        assertThat(access.canSee(admin, INVOICE, invoice)).isTrue();
    }

    @Test
    void warehouseSeesOrdersThroughItsDeliveries() {
        Scope inA = scope(f.user("wa", Role.WAREHOUSE), Role.WAREHOUSE, whA);
        Scope inB = scope(f.user("wb", Role.WAREHOUSE), Role.WAREHOUSE, whB);
        assertThat(access.canSee(inA, ORDER, order)).isTrue();
        assertThat(access.canSee(inA, INVOICE, invoice)).isTrue();
        assertThat(access.canSee(inB, ORDER, order)).isFalse();
        assertThat(access.canSee(inA, CUSTOMER, acme)).isFalse();
        readOnly(() -> access.requireWritable(inA, ORDER, order));
    }

    @Test
    void writableRules() {
        assertThatCode(() -> access.requireWritable(rep, ORDER, order)).doesNotThrowAnyException();
        long voidInvoice = f.invoice(order, "VOID");
        readOnly(() -> access.requireWritable(rep, INVOICE, voidInvoice));
        assertThatCode(() -> access.requireWritable(rep, ORDER, order)).doesNotThrowAnyException();
        long cancelled = f.order(acme, "CANCELLED");
        readOnly(() -> access.requireWritable(rep, ORDER, cancelled));
        f.archiveCustomer(acme);
        readOnly(() -> access.requireWritable(rep, CUSTOMER, acme));
    }

    @Test
    void unknownOrInvisibleIsNotFound() {
        assertThatThrownBy(() -> access.requireWritable(admin, ORDER, -1)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> access.requireWritable(other, CUSTOMER, acme)).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(access.invoiceIdsOfOrder(order)).containsExactly(invoice);
    }
}
