package ph.thecoffeejunkie.crm.deals;

/** A deal entered a new stage (including Won and Lost). Published inside the transaction that did it. */
public record DealStageChanged(long dealId, DealStage stage) {
}
