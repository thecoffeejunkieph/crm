package ph.thecoffeejunkie.crm.identity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ph.thecoffeejunkie.crm.MailIntegrationTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@MailIntegrationTest
class OneTimeTokensIT {

    // one_time_token.subject_id has no FK, so any id works for subject type TEST.
    static final long SUBJECT = 9_000_001L;

    @Autowired OneTimeTokens tokens;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("delete from one_time_token where subject_type = 'TEST'");
    }

    @Test
    void storesOnlyHash() {
        String raw = tokens.issue(TokenPurpose.INVITE, "TEST", SUBJECT, Duration.ofHours(1), null);
        List<String> hashes = jdbc.queryForList(
                "select token_hash from one_time_token where subject_type = 'TEST'", String.class);
        assertThat(hashes).hasSize(1).doesNotContain(raw);
        assertThat(hashes.get(0)).doesNotContain(raw).hasSize(64);
        assertThat(tokens.redeem(TokenPurpose.INVITE, raw)).contains(new OneTimeTokens.Subject("TEST", SUBJECT));
    }

    @Test
    void expiredTokenRejected() {
        String raw = tokens.issue(TokenPurpose.INVITE, "TEST", SUBJECT, Duration.ofSeconds(-1), null);
        assertThat(tokens.redeem(TokenPurpose.INVITE, raw)).isEmpty();
    }

    @Test
    void wrongPurposeAndUnknownTokenRejected() {
        String raw = tokens.issue(TokenPurpose.INVITE, "TEST", SUBJECT, Duration.ofHours(1), null);
        assertThat(tokens.redeem(TokenPurpose.PASSWORD_RESET, raw)).isEmpty();
        assertThat(tokens.redeem(TokenPurpose.INVITE, "not-a-token")).isEmpty();
        assertThat(tokens.redeem(TokenPurpose.INVITE, raw)).isPresent();
    }

    @Test
    void redeemIsSingleUse() {
        String raw = tokens.issue(TokenPurpose.INVITE, "TEST", SUBJECT, Duration.ofHours(1), null);
        assertThat(tokens.redeem(TokenPurpose.INVITE, raw)).isPresent();
        assertThat(tokens.redeem(TokenPurpose.INVITE, raw)).isEmpty();
    }

    @Test
    void concurrentRedeemOnlyOneWins() {
        String raw = tokens.issue(TokenPurpose.PASSWORD_RESET, "TEST", SUBJECT, Duration.ofHours(1), null);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Optional<OneTimeTokens.Subject>>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                return tokens.redeem(TokenPurpose.PASSWORD_RESET, raw);
            }));
        }
        start.countDown();
        assertThat(futures.stream().map(CompletableFuture::join).filter(Optional::isPresent)).hasSize(1);
    }

    @Test
    void reissueInvalidatesPrevious() {
        String first = tokens.issue(TokenPurpose.INVITE, "TEST", SUBJECT, Duration.ofHours(1), null);
        String second = tokens.issue(TokenPurpose.INVITE, "TEST", SUBJECT, Duration.ofHours(1), null);
        assertThat(tokens.redeem(TokenPurpose.INVITE, first)).isEmpty();
        assertThat(tokens.redeem(TokenPurpose.INVITE, second)).isPresent();
    }

    @Test
    void reissueLeavesOtherPurposesAndSubjectsAlone() {
        String otherPurpose = tokens.issue(TokenPurpose.PASSWORD_RESET, "TEST", SUBJECT, Duration.ofHours(1), null);
        String otherSubject = tokens.issue(TokenPurpose.INVITE, "TEST", SUBJECT + 1, Duration.ofHours(1), null);
        tokens.issue(TokenPurpose.INVITE, "TEST", SUBJECT, Duration.ofHours(1), null);
        assertThat(tokens.redeem(TokenPurpose.PASSWORD_RESET, otherPurpose)).isPresent();
        assertThat(tokens.redeem(TokenPurpose.INVITE, otherSubject)).isPresent();
    }
}
