package ph.thecoffeejunkie.crm.identity;

/** Login accepts either field; enable and disable use code. */
record TotpCodeRequest(String code, String recoveryCode) {
}
