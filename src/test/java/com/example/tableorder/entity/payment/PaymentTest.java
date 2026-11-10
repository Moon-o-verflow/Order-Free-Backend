package com.example.tableorder.entity.payment;

import com.example.tableorder.entity.order.Order;
import com.example.tableorder.exception.BadRequestException;
import com.example.tableorder.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private final Order order = Order.builder().id(1L).totalPrice(15000).build();

    @Test
    void readyHoldsServerAmount() {
        Payment p = Payment.ready(order, "OF-1-abc", 15000);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(p.getAmount()).isEqualTo(15000);
    }

    @Test
    void zeroAmountCannotBePaid() {
        assertThatThrownBy(() -> Payment.ready(order, "OF-1-abc", 0)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void amountMustMatchExactly() {
        Payment p = Payment.ready(order, "OF-1-abc", 15000);
        p.verifyAmount(15000);
        assertThatThrownBy(() -> p.verifyAmount(14999)).isInstanceOf(PaymentAmountMismatchException.class);
        assertThatThrownBy(() -> p.verifyAmount(null)).isInstanceOf(PaymentAmountMismatchException.class);
    }

    @Test
    void onlyFailedPaymentCanBeRetried() {
        Payment p = Payment.ready(order, "OF-1-abc", 15000);
        assertThatThrownBy(() -> p.retry("OF-1-new")).isInstanceOf(BusinessRuleException.class);

        ReflectionTestUtils.setField(p, "status", PaymentStatus.FAILED);
        ReflectionTestUtils.setField(p, "failureCode", "REJECT_CARD_COMPANY");
        p.retry("OF-1-new");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(p.getPgOrderId()).isEqualTo("OF-1-new");
        assertThat(p.getFailureCode()).isNull();
    }

    @Test
    void transitions() {
        assertThat(PaymentStatus.READY.nextStatuses()).containsExactly(PaymentStatus.IN_PROGRESS);
        assertThat(PaymentStatus.IN_PROGRESS.nextStatuses())
                .containsExactlyInAnyOrder(PaymentStatus.DONE, PaymentStatus.FAILED, PaymentStatus.UNKNOWN);
        assertThat(PaymentStatus.UNKNOWN.canTransitionTo(PaymentStatus.IN_PROGRESS)).isFalse(); // 재승인 금지
        assertThat(PaymentStatus.DONE.nextStatuses()).containsExactly(PaymentStatus.CANCELED);
        assertThat(PaymentStatus.CANCELED.nextStatuses()).isEmpty();
    }

    @Test
    void orderPaymentStatusMapping() {
        assertThat(OrderPaymentStatus.of(null)).isEqualTo(OrderPaymentStatus.UNPAID);
        assertThat(OrderPaymentStatus.of(PaymentStatus.IN_PROGRESS)).isEqualTo(OrderPaymentStatus.UNPAID);
        assertThat(OrderPaymentStatus.of(PaymentStatus.DONE)).isEqualTo(OrderPaymentStatus.PAID);
        assertThat(OrderPaymentStatus.of(PaymentStatus.CANCELED)).isEqualTo(OrderPaymentStatus.REFUNDED);
    }
}
