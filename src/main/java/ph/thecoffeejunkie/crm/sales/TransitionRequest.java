package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.constraints.NotNull;

/** Body of every order transition: the version the caller saw, and a reason where one is needed. */
record TransitionRequest(@NotNull Long version, String reason) {
}
