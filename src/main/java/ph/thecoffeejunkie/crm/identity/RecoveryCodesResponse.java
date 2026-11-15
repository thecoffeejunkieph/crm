package ph.thecoffeejunkie.crm.identity;

import java.util.List;

/** sessionToken is set only when enrolment upgraded an ANDROID session (the id rotates). */
record RecoveryCodesResponse(List<String> codes, String sessionToken) {
}
