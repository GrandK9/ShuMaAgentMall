package com.shumamall.payment.controller.api;

import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.result.R;
import com.shumamall.payment.dto.PayRequestDTO;
import com.shumamall.payment.dto.PayResponseDTO;
import com.shumamall.payment.dto.PaymentRecordDTO;
import com.shumamall.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 支付控制器（用户端）。
 */
@Tag(name = "支付-用户端", description = "用户端模拟支付发起与支付记录查询接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 模拟支付。
     * <p>
     * 立即返回支付成功结果，并同步更新订单状态。
     *
     * @param dto 支付请求参数
     * @return 支付结果
     */
    @Operation(summary = "模拟支付")
    @PostMapping("/pay")
    public R<PayResponseDTO> pay(@Valid @RequestBody PayRequestDTO dto) {
        Long userId = SecurityContext.getUserId();
        PayResponseDTO result = paymentService.pay(userId, dto);
        return R.ok(result);
    }

    /**
     * 根据支付流水号查询支付记录。
     *
     * @param paymentNo 支付流水号
     * @return 支付记录
     */
    @Operation(summary = "根据支付流水号查询支付记录")
    @GetMapping("/{paymentNo}")
    public R<PaymentRecordDTO> getByPaymentNo(
            @Parameter(description = "支付流水号") @PathVariable String paymentNo) {
        Long userId = SecurityContext.getUserId();
        PaymentRecordDTO result = paymentService.getByPaymentNo(paymentNo, userId);
        return R.ok(result);
    }

    /**
     * 根据订单编号查询所有支付记录。
     *
     * @param orderNo 订单编号
     * @return 支付记录列表
     */
    @Operation(summary = "根据订单编号查询所有支付记录")
    @GetMapping("/order/{orderNo}")
    public R<List<PaymentRecordDTO>> getByOrderNo(
            @Parameter(description = "订单编号") @PathVariable String orderNo) {
        Long userId = SecurityContext.getUserId();
        List<PaymentRecordDTO> result = paymentService.getByOrderNo(orderNo, userId);
        return R.ok(result);
    }
}
