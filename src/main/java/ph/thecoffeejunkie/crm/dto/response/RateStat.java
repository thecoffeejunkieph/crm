package ph.thecoffeejunkie.crm.dto.response;

/** value and deltaPoints are both percentages (0-100); deltaPoints is the change in percentage points. */
public record RateStat(double value, Double deltaPoints) {}
