package ph.thecoffeejunkie.crm.shared;

/** One key per phone number however it was typed: digits only, and +63 mobile numbers written the local way. */
public final class PhoneKey {

    private PhoneKey() {
    }

    /** The same rule in SQL, for a column or expression {@code col}. Keep in step with {@link #of}. */
    public static String sql(String col) {
        String d = "regexp_replace(coalesce(" + col + ", ''), '\\D', '', 'g')";
        return "(case when length(" + d + ") = 12 and " + d + " like '63%' then '0' || substr(" + d + ", 3) else "
                + d + " end)";
    }

    /** Null when there are fewer than 7 digits: too short to mean anyone. */
    public static String of(String raw) {
        if (raw == null) {
            return null;
        }
        String d = raw.replaceAll("\\D", "");
        if (d.length() < 7) {
            return null;
        }
        return d.length() == 12 && d.startsWith("63") ? "0" + d.substring(2) : d;
    }
}
