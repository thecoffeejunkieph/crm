package ph.thecoffeejunkie.crm.sales;

import java.util.List;

/** emailedTo is empty when the customer has no portal contacts: nobody was mailed. */
record ResendResponse(List<String> emailedTo) {
}
