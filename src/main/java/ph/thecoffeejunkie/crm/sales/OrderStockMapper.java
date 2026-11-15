package ph.thecoffeejunkie.crm.sales;

import ph.thecoffeejunkie.crm.inventory.OrderStock;
import ph.thecoffeejunkie.crm.inventory.ReserveLine;

/** What inventory needs to reserve an order: its lines, ship-from warehouse and rep (the creator when unassigned). */
final class OrderStockMapper {

    private OrderStockMapper() {
    }

    static OrderStock of(SalesOrder o) {
        return new OrderStock(o.id, o.number, rep(o), o.shipFromWarehouseId, o.confirmedAt,
                o.lines.stream().map(l -> new ReserveLine(l.id, l.productId, l.qty)).toList());
    }

    static long rep(SalesOrder o) {
        return o.repId != null ? o.repId : o.createdBy;
    }
}
