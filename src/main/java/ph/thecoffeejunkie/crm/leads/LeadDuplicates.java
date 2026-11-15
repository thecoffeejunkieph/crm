package ph.thecoffeejunkie.crm.leads;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.customers.ContactMatches;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.shared.PhoneKey;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The "someone already has this person" warning on the lead form. Never blocks a save. */
@Service
class LeadDuplicates {

    record LeadDuplicatesResponse(List<ContactMatches.Match> leads, List<ContactMatches.Match> customers, int hidden) {
    }

    private final JdbcTemplate jdbc;
    private final Customers customers;

    LeadDuplicates(JdbcTemplate jdbc, Customers customers) {
        this.jdbc = jdbc;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    LeadDuplicatesResponse find(Scope s, String phone, String email, Long excludeId) {
        String key = PhoneKey.of(phone);
        String mail = email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
        ContactMatches leads = matchLeads(s, mail, key, excludeId);
        ContactMatches custs = customers.matchContact(s, mail, key);
        return new LeadDuplicatesResponse(leads.visible(), custs.visible(), leads.hidden() + custs.hidden());
    }

    private ContactMatches matchLeads(Scope s, String email, String phoneKey, Long excludeId) {
        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (email != null) {
            where.add("lower(email) = ?");
            args.add(email);
        }
        if (phoneKey != null) {
            where.add(PhoneKey.sql("phone") + " = ?");
            args.add(phoneKey);
        }
        if (where.isEmpty()) {
            return new ContactMatches(List.of(), 0);
        }
        args.add(excludeId == null ? -1L : excludeId);
        List<ContactMatches.Candidate> rows = jdbc.query("select id, contact_name, assigned_rep_id from lead "
                        + "where status in ('NEW','CONTACTED','QUALIFIED') and (" + String.join(" or ", where)
                        + ") and id <> ? order by id limit 200",
                (rs, i) -> new ContactMatches.Candidate(rs.getLong(1), rs.getString(2), rs.getObject(3, Long.class)),
                args.toArray());
        return ContactMatches.split(rows, s);
    }
}
