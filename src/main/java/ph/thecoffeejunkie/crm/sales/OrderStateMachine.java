package ph.thecoffeejunkie.crm.sales;

import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static ph.thecoffeejunkie.crm.sales.OrderAction.*;

/** Which actions a status allows. Invoice presence, expiry and payments are the services' checks, not this table's. */
final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderAction>> ALLOWED = new EnumMap<>(Map.of(
            OrderStatus.DRAFT, EnumSet.of(EDIT, SEND_QUOTE, CONFIRM, CANCEL),
            OrderStatus.QUOTED, EnumSet.of(EDIT, SEND_QUOTE, CONFIRM, ACCEPT, DECLINE, CANCEL),
            OrderStatus.CONFIRMED, EnumSet.of(REVISE, RELEASE, CANCEL, ISSUE_INVOICE, REISSUE_INVOICE, RECORD_PAYMENT),
            OrderStatus.RELEASED, EnumSet.of(REVISE, FULFIL, CANCEL, ISSUE_INVOICE, REISSUE_INVOICE, RECORD_PAYMENT),
            OrderStatus.FULFILLED, EnumSet.of(ISSUE_INVOICE, REISSUE_INVOICE, RECORD_PAYMENT),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderAction.class)));

    private OrderStateMachine() {
    }

    static Set<OrderAction> allowed(OrderStatus s) {
        return EnumSet.copyOf(ALLOWED.get(s));
    }

    static void require(OrderStatus s, OrderAction a) {
        if (!ALLOWED.get(s).contains(a)) {
            throw new DomainException(ErrorCode.INVALID_STATE,
                    "Can't " + a.name().toLowerCase().replace('_', ' ') + " a " + s.name().toLowerCase() + " order");
        }
    }
}
