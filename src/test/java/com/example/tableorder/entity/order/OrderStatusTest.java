package com.example.tableorder.entity.order;

import com.example.tableorder.exception.BadRequestException;
import com.example.tableorder.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    private static Order orderIn(OrderStatus status) {
        Order order = Order.builder().build();
        // 테스트 대상 상태까지 정상 경로로 이동
        switch (status) {
            case ORDERED -> { }
            case PREPARING -> order.changeStatus(OrderStatus.PREPARING);
            case SERVED -> {
                order.changeStatus(OrderStatus.PREPARING);
                order.changeStatus(OrderStatus.SERVED);
            }
            case COMPLETED -> {
                order.changeStatus(OrderStatus.PREPARING);
                order.changeStatus(OrderStatus.SERVED);
                order.changeStatus(OrderStatus.COMPLETED);
            }
            case CANCELLED -> order.changeStatus(OrderStatus.CANCELLED);
        }
        return order;
    }

    @Test
    void newOrderStartsAsOrdered() {
        assertThat(Order.builder().build().getStatus()).isEqualTo(OrderStatus.ORDERED);
    }

    @ParameterizedTest
    @CsvSource({
            "ORDERED, PREPARING", "ORDERED, CANCELLED",
            "PREPARING, SERVED", "PREPARING, CANCELLED",
            "SERVED, COMPLETED",
    })
    void allowedTransitions(OrderStatus from, OrderStatus to) {
        Order order = orderIn(from);
        order.changeStatus(to);
        assertThat(order.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "ORDERED, SERVED", "ORDERED, COMPLETED",
            "PREPARING, ORDERED", "PREPARING, COMPLETED",
            "SERVED, CANCELLED", "SERVED, PREPARING",
            "COMPLETED, ORDERED", "COMPLETED, CANCELLED",
            "CANCELLED, ORDERED", "CANCELLED, PREPARING",
    })
    void rejectedTransitions(OrderStatus from, OrderStatus to) {
        Order order = orderIn(from);
        assertThatThrownBy(() -> order.changeStatus(to)).isInstanceOf(BusinessRuleException.class);
        assertThat(order.getStatus()).isEqualTo(from);
    }

    @Test
    void sameStatusIsNoOp() {
        Order order = orderIn(OrderStatus.COMPLETED);
        order.changeStatus(OrderStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void parsesCaseInsensitively() {
        assertThat(OrderStatus.from(" served ")).isEqualTo(OrderStatus.SERVED);
        assertThat(OrderStatus.from("Cancelled")).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void unknownValueIsBadRequest() {
        assertThatThrownBy(() -> OrderStatus.from("Done")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> OrderStatus.from(null)).isInstanceOf(BadRequestException.class);
    }
}
