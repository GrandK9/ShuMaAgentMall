package com.shumamall.payment.service;

import com.shumamall.common.result.PageResult;
import com.shumamall.payment.dto.PayRequestDTO;
import com.shumamall.payment.dto.PayResponseDTO;
import com.shumamall.payment.dto.PaymentRecordDTO;

import java.util.List;

/**
 * 支付服务接口。
 */
public interface PaymentService {

    /**
     * 模拟支付。
     * <p>
     * 生成支付流水号并创建支付记录，状态直接置为支付成功。
     * 同时通过 Feign 调用订单服务更新订单状态为已支付。
     *
     * @param userId 用户ID
     * @param dto    支付请求参数
     * @return 支付结果
     */
    PayResponseDTO pay(Long userId, PayRequestDTO dto);

    /**
     * 根据支付流水号查询支付记录（仅限本人，他人流水拒绝访问）。
     *
     * @param paymentNo 支付流水号
     * @param userId    查询人用户ID
     * @return 支付记录
     */
    PaymentRecordDTO getByPaymentNo(String paymentNo, Long userId);

    /**
     * 根据订单编号查询支付记录（只返回本人的记录）。
     *
     * @param orderNo 订单编号
     * @param userId  查询人用户ID
     * @return 支付记录列表
     */
    List<PaymentRecordDTO> getByOrderNo(String orderNo, Long userId);

    /**
     * 管理端分页查询支付记录。
     *
     * @param page 页码
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<PaymentRecordDTO> adminPageQuery(Integer page, Integer size);

    /**
     * 管理端退款。
     * <p>
     * 将支付记录状态更新为已退款（3），并调用订单服务更新订单状态为已取消。
     *
     * @param id          支付记录ID
     * @param adminUserId 操作的管理员用户ID
     */
    void refund(Long id, Long adminUserId);
}
