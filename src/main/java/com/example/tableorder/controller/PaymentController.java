package com.example.tableorder.controller;

import com.example.tableorder.dto.payment.PaymentResponse;
import com.example.tableorder.service.payment.PaymentFacade;
import com.example.tableorder.service.payment.PaymentTxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 손님용 결제 API.
 * - 준비는 손님 경로(매장·테이블) 아래에 두어, 그 테이블의 주문만 결제할 수 있게 한다.
 * - 조회는 추측할 수 없는 PG 주문번호로만 한다(순번 ID로 남의 결제를 훑지 못하게).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "결제", description = "토스페이먼츠 결제 API")
public class PaymentController {

    private final PaymentFacade paymentFacade;
    private final PaymentTxService paymentTxService;

    @PostMapping("/api/stores/{storeId}/tables/{tableId}/orders/{orderId}/payments")
    @Operation(summary = "결제 준비",
            description = "주문의 결제를 READY 로 만들고 PG 주문번호와 서버 계산 금액을 돌려줍니다. "
                    + "이미 있으면 새로 만들지 않고 기존 결제를 200으로 돌려줍니다.")
    public ResponseEntity<PaymentResponse> prepare(
            @Parameter(example = "1") @PathVariable Long storeId,
            @Parameter(example = "1") @PathVariable Long tableId,
            @Parameter(example = "1") @PathVariable Long orderId) {
        PaymentTxService.PrepareResult result = paymentFacade.prepare(storeId, tableId, orderId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.payment());
    }

    @GetMapping("/api/payments/{pgOrderId}")
    @Operation(summary = "결제 조회", description = "PG 주문번호로 결제 상태를 조회합니다.")
    public PaymentResponse get(@PathVariable String pgOrderId) {
        return paymentTxService.findByPgOrderId(pgOrderId);
    }
}
