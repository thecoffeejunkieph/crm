package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ph.thecoffeejunkie.crm.sales.OrderAction.*;

class OrderStateMachineTest {

    // Spec §3 / plan Task 5 table, written out independently of the implementation.
    private static final Map<OrderStatus, Set<OrderAction>> TABLE = Map.of(
            OrderStatus.DRAFT, EnumSet.of(EDIT, SEND_QUOTE, CONFIRM, CANCEL),
            OrderStatus.QUOTED, EnumSet.of(EDIT, SEND_QUOTE, CONFIRM, ACCEPT, DECLINE, CANCEL),
            OrderStatus.CONFIRMED, EnumSet.of(REVISE, RELEASE, CANCEL, ISSUE_INVOICE, REISSUE_INVOICE, RECORD_PAYMENT,
                    COVER_SHORTAGE),
            OrderStatus.RELEASED, EnumSet.of(REVISE, CANCEL, ISSUE_INVOICE, REISSUE_INVOICE, RECORD_PAYMENT),
            OrderStatus.FULFILLED, EnumSet.of(ISSUE_INVOICE, REISSUE_INVOICE, RECORD_PAYMENT),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderAction.class));

    static Stream<Arguments> everyPair() {
        return Stream.of(OrderStatus.values())
                .flatMap(s -> Stream.of(OrderAction.values()).map(a -> Arguments.of(s, a)));
    }

    @ParameterizedTest
    @MethodSource("everyPair")
    void allowedIffInTable(OrderStatus s, OrderAction a) {
        boolean expected = TABLE.get(s).contains(a);
        assertThat(OrderStateMachine.allowed(s).contains(a)).isEqualTo(expected);
        if (expected) {
            assertThatCode(() -> OrderStateMachine.require(s, a)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> OrderStateMachine.require(s, a))
                    .isInstanceOfSatisfying(DomainException.class, e -> {
                        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE);
                        assertThat(e.getMessage()).contains(a.name().toLowerCase().replace('_', ' '))
                                .contains(s.name().toLowerCase());
                    });
        }
    }
}
