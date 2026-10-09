package ph.thecoffeejunkie.crm.dto.response;

import java.io.Serializable;

public record CountStat(long value, Double deltaPercent) implements Serializable {}
