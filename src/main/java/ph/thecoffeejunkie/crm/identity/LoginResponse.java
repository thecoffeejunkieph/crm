package ph.thecoffeejunkie.crm.identity;

/** next: TOTP, ENROL_TOTP or DONE. sessionToken is set only for ANDROID clients. */
record LoginResponse(String next, String sessionToken) {
}
