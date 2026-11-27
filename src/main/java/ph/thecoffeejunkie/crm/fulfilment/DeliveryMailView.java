package ph.thecoffeejunkie.crm.fulfilment;

import ph.thecoffeejunkie.crm.shared.HtmlMail;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A delivery order as the customer status mails show it, photos already read from storage. */
public record DeliveryMailView(String number, String orderNumber, DeliveryMethod method, String shipTo,
                               String receiverName, Instant deliveredAt, List<Line> lines,
                               List<HtmlMail.Attachment> photos, int photosLeftOut) {

    public record Line(String description, BigDecimal qty, String unit) {
    }
}
