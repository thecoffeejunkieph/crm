package ph.thecoffeejunkie.crm.followups;

/** Why the system created a follow-up. Null on a follow-up means a rep wrote it. */
public enum FollowUpRule {
    RESTOCK, WIN_BACK, QUOTE_CHASE, QUOTE_EXPIRING, QUOTE_DECLINED, FIRST_ORDER, PAYMENT_OVERDUE, DEAL_STALLED
}
