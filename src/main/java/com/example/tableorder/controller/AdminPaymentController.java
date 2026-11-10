package com.example.tableorder.controller;

import com.example.tableorder.dto.payment.PaymentResponse;
import com.example.tableorder.service.payment.PaymentFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 점주용 결제 API. 자기 매장 결제만 다룰 수 있다. */
@RestController
@RequiredArgsConstructor
@Tag(name = "결제(점주)", description = "점주 결제 취소")
public class AdminPaymentController {

    private final PaymentFacade paymentFacade;

    public record CancelRequest(@NotBlank @Size(max = 200) String reason) {
    }

    @PostMapping("/api/admin/payments/{paymentId}/cancel")
    @PreAuthorize("hasRole('OWNER') and @storeAccess.ownsPayment(#paymentId)")
    @Operation(summary = "결제 전액 취소",
            description = "DONE 결제만 취소합니다. 같은 요청을 다시 보내도 환불은 한 번만 됩니다. "
                    + "200 CANCELED · 409 DONE 아님/PG 거절 · 503 결과 확인 실패(재시도 안전)")
    public PaymentResponse cancel(@PathVariable Long paymentId, @Valid @RequestBody CancelRequest request) {
        return paymentFacade.cancel(paymentId, request.reason());
    }
}
