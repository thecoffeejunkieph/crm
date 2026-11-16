package ph.thecoffeejunkie.crm.followups;

import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;

import java.util.EnumSet;
import java.util.Set;

/** The follow-up settings read once per scan, so every rule sees the same values. */
public record RuleSettings(Set<FollowUpRule> enabled, int restockFallbackDays, int restockRhythmPercent,
                           int restockMinDays, int winBackDays, int quoteChaseDays, int quoteExpiringDaysBefore,
                           int firstOrderDays, int dealStalledDays, Long fallbackAdminId) {

    public static RuleSettings from(Settings s) {
        Set<FollowUpRule> on = EnumSet.noneOf(FollowUpRule.class);
        for (FollowUpRule r : FollowUpRule.values()) {
            if (s.bool(SettingKey.valueOf("FOLLOW_UPS_AUTO_" + r.name() + "_ENABLED"))) {
                on.add(r);
            }
        }
        return new RuleSettings(Set.copyOf(on), s.integer(SettingKey.FOLLOW_UPS_RESTOCK_FALLBACK_DAYS),
                s.integer(SettingKey.FOLLOW_UPS_RESTOCK_RHYTHM_PERCENT), s.integer(SettingKey.FOLLOW_UPS_RESTOCK_MIN_DAYS),
                s.integer(SettingKey.FOLLOW_UPS_WIN_BACK_DAYS), s.integer(SettingKey.FOLLOW_UPS_QUOTE_CHASE_DAYS),
                s.integer(SettingKey.FOLLOW_UPS_QUOTE_EXPIRING_DAYS_BEFORE), s.integer(SettingKey.FOLLOW_UPS_FIRST_ORDER_DAYS),
                s.integer(SettingKey.FOLLOW_UPS_DEAL_STALLED_DAYS), s.longOrNull(SettingKey.FOLLOW_UPS_FALLBACK_ADMIN_ID));
    }
}
