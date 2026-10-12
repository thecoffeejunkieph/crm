package ph.thecoffeejunkie.crm.identity;

/** next: TOTP, ENROL_TOTP or DONE. sessionToken is set only for X-Client-Type: ANDROID. */
record LoginResponse(String next, String sessionToken) {
}
