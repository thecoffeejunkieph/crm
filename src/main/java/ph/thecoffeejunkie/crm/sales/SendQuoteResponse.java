package ph.thecoffeejunkie.crm.sales;

import java.util.List;

/** emailedTo is empty when the customer has no portal contacts: the quote is marked sent but nobody was mailed. */
record SendQuoteResponse(OrderResponse order, List<String> emailedTo) {
}
