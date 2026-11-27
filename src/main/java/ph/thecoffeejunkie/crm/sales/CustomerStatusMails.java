package ph.thecoffeejunkie.crm.sales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.fulfilment.DeliveryDispatched;
import ph.thecoffeejunkie.crm.fulfilment.DeliveryMailView;
import ph.thecoffeejunkie.crm.fulfilment.DeliveryMethod;
import ph.thecoffeejunkie.crm.fulfilment.Fulfilment;
import ph.thecoffeejunkie.crm.fulfilment.OrderDelivered;
import ph.thecoffeejunkie.crm.shared.HtmlMail;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Tells a customer's portal contacts where their order stands. Every handler runs after the action commits, reads
 * on the task executor and sends there, so a rolled-back action mails nobody and a failed send never undoes one.
 */
@Component
class CustomerStatusMails {

    private static final Logger log = LoggerFactory.getLogger(CustomerStatusMails.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
            .withZone(ZoneId.of("Asia/Manila"));

    private final HtmlMail mail;
    private final TaskExecutor executor;
    private final SalesOrderRepository orders;
    private final PaymentRepository payments;
    private final Customers customers;
    private final Fulfilment fulfilment;
    private final TransactionTemplate read;
    private final String link;

    CustomerStatusMails(HtmlMail mail, @Qualifier("applicationTaskExecutor") TaskExecutor executor,
                        SalesOrderRepository orders, PaymentRepository payments, Customers customers,
                        Fulfilment fulfilment, PlatformTransactionManager tx, @Value("${app.frontend-url}") String frontendUrl) {
        this.mail = mail;
        this.executor = executor;
        this.orders = orders;
        this.payments = payments;
        this.customers = customers;
        this.fulfilment = fulfilment;
        this.read = new TransactionTemplate(tx);
        this.read.setReadOnly(true);
        this.link = frontendUrl + "/portal/signin";
    }

    /** One message to send: who, what, and the template's variables. */
    private record Mail(List<String> to, String subject, String template, Map<String, Object> vars,
                        List<HtmlMail.Attachment> files) {
    }

    @TransactionalEventListener
    public void on(OrderConfirmed e) {
        later(() -> {
            SalesOrder o = orders.findById(e.orderId()).orElseThrow();
            return new Mail(recipients(o), "Order " + o.number + " confirmed — " + mail.company(), "order-confirmed",
                    Map.of("number", o.number, "total", o.total.toPlainString(), "link", link), List.of());
        });
    }

    @TransactionalEventListener
    public void on(PaymentVerified e) {
        later(() -> {
            SalesOrder o = orders.findById(e.orderId()).orElseThrow();
            Payment p = payments.findById(e.paymentId()).orElseThrow();
            return new Mail(recipients(o), "Payment received for " + o.number + " — " + mail.company(),
                    "payment-received", Map.of("number", o.number, "amount", p.amount().toPlainString(),
                    "method", p.method.name(), "receivedOn", p.receivedOn.toString(),
                    "balance", o.balance().toPlainString(), "link", link), List.of());
        });
    }

    /** Only a customer's own portal upload is answered; a rep's record being corrected is internal. */
    @TransactionalEventListener
    public void on(PaymentRejected e) {
        later(() -> {
            Payment p = payments.findById(e.paymentId()).orElseThrow();
            if (!PaymentService.CONTACT.equals(p.submittedByType)) {
                return null;
            }
            SalesOrder o = orders.findById(e.orderId()).orElseThrow();
            return new Mail(recipients(o), "Payment for " + o.number + " was not accepted — " + mail.company(),
                    "payment-rejected", Map.of("number", o.number, "amount", p.amount().toPlainString(),
                    "reason", p.rejectReason, "link", link), List.of());
        });
    }

    @TransactionalEventListener
    public void on(DeliveryDispatched e) {
        later(() -> {
            DeliveryMailView v = view(e.deliveryOrderId());
            if (v == null) {
                return null;
            }
            SalesOrder o = orders.findById(e.salesOrderId()).orElseThrow();
            return new Mail(recipients(o), "Order " + v.orderNumber() + " is out for delivery — " + mail.company(),
                    "order-dispatched", Map.of("orderNumber", v.orderNumber(), "number", v.number(),
                    "shipTo", v.shipTo() == null ? "" : v.shipTo(), "lines", v.lines(), "link", link), List.of());
        });
    }

    @TransactionalEventListener
    public void on(OrderDelivered e) {
        later(() -> {
            DeliveryMailView v = view(e.deliveryOrderId());
            if (v == null) {
                return null;
            }
            SalesOrder o = orders.findById(e.orderId()).orElseThrow();
            boolean pickup = v.method() == DeliveryMethod.PICKUP;
            Map<String, Object> vars = new HashMap<>();
            vars.put("orderNumber", v.orderNumber());
            vars.put("number", v.number());
            vars.put("receiverName", v.receiverName());
            vars.put("deliveredAt", WHEN.format(v.deliveredAt()));
            vars.put("lines", v.lines());
            vars.put("pickup", pickup);
            vars.put("photosLeftOut", v.photosLeftOut());
            vars.put("link", link);
            return new Mail(recipients(o), "Order " + v.orderNumber() + (pickup ? " picked up — " : " delivered — ")
                    + mail.company(), "order-delivered", vars, v.photos());
        });
    }

    private DeliveryMailView view(long deliveryOrderId) {
        DeliveryMailView v = fulfilment.mailView(deliveryOrderId).orElse(null);
        if (v == null) {
            log.warn("Delivery order {} not found for its customer mail", deliveryOrderId);
        }
        return v;
    }

    private List<String> recipients(SalesOrder o) {
        return customers.portalContactEmails(o.customerId);
    }

    /** Builds the mail in a read-only transaction on the executor, then sends it outside that transaction. */
    private void later(Supplier<Mail> build) {
        executor.execute(() -> {
            Mail m;
            try {
                m = read.execute(s -> build.get());
            } catch (RuntimeException ex) {
                log.warn("Customer status mail not built", ex);
                return;
            }
            if (m == null) {
                return;
            }
            for (String address : m.to()) {
                try {
                    mail.send(address, m.subject(), m.template(), m.vars(), m.files());
                } catch (RuntimeException ex) {
                    log.warn("{} mail to {} failed", m.template(), address, ex);
                }
            }
        });
    }
}
