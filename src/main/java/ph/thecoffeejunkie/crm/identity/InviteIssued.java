package ph.thecoffeejunkie.crm.identity;

/** Published inside the invite transaction; the mail goes out only once it has committed. */
record InviteIssued(long userId, String link) {
}
