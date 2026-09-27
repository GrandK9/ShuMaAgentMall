package com.shumamall.payment.controller.admin;

import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.perm.annotation.RequirePermission;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.payment.dto.PaymentRecordDTO;
import com.shumamall.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 支付管理控制器（管理端）。
 */
@Slf4j
@Tag(name = "支付-支付管理(后台)", description = "管理端支付记录分页查询与退款操作")
@RestController
@RequestMapping("/api/v1/admin/payment")
@RequiredArgsConstructor
public class PaymentAdminController {

    private final PaymentService paymentService;

    /**
     * 分页查询所有支付记录。
     *
     * @param page 页码
     * @param size 每页条数
     * @return 分页支付记录
     */
    @GetMapping
    @RequirePermission("order:view")
    public R<PageResult<PaymentRecordDTO>> list(@RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "20") Integer size) {
        PageResult<PaymentRecordDTO> result = paymentService.adminPageQuery(page, size);
        return R.ok(result);
    }

    /**
     * 管理端退款操作。
     * <p>
     * 将指定支付记录标记为已退款，并同步更新订单状态。
     *
     * @param id 支付记录ID
     * @return 操作结果
     */
    @PostMapping("/{id}/refund")
    @RequirePermission("order:refund")
    public R<Void> refund(@PathVariable Long id) {
        Long adminUserId = SecurityContext.getUserId();
        paymentService.refund(id, adminUserId);
        log.info("管理端退款操作: paymentId={}, adminUserId={}", id, adminUserId);
        return R.ok();
    }
}
