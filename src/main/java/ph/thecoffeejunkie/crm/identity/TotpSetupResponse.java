package ph.thecoffeejunkie.crm.identity;

record TotpSetupResponse(String otpauthUri, String secret, String qrSvg) {
}
