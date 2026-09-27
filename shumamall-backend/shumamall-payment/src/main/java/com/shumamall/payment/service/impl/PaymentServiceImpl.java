package com.shumamall.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import com.shumamall.payment.api.OrderFeignClient;
import com.shumamall.payment.dao.PaymentRecordMapper;
import com.shumamall.payment.dto.PayRequestDTO;
import com.shumamall.payment.dto.PayResponseDTO;
import com.shumamall.payment.dto.PaymentRecordDTO;
import com.shumamall.payment.entity.PaymentRecordEntity;
import com.shumamall.payment.service.PaymentService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 支付服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRecordMapper paymentRecordMapper;
    private final OrderFeignClient orderFeignClient;
    private final ObjectMapper objectMapper;

    /**
     * 支付状态标签映射。
     */
    private static String getStatusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "支付成功";
            case 2 -> "支付失败";
            case 3 -> "已退款";
            default -> "未知";
        };
    }

    @Override
    @GlobalTransactional(name = "pay-order", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public PayResponseDTO pay(Long userId, PayRequestDTO dto) {
        // 金额强校验：支付金额以订单服务的应付金额为唯一依据，客户端传入值只作比对。
        // 否则客户端可传 amount=0.01 支付任意金额订单（旧实现直接将请求体金额落库并置订单已支付）。
        BigDecimal paidAmount = dto.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal payableAmount = fetchPayableAmount(dto.getOrderNo(), userId);
        if (paidAmount.compareTo(payableAmount) != 0) {
            log.warn("支付金额校验失败: orderNo={}, paidAmount={}, payableAmount={}, userId={}",
                    dto.getOrderNo(), paidAmount, payableAmount, userId);
            throw new BusinessException(ResultCode.PAY_AMOUNT_MISMATCH);
        }

        // 幂等防线①（快速路径）：同一订单已存在支付记录时直接拒绝，覆盖串行重复提交
        // （用户连点两次、前端重试、网关重试等）。并发场景由下方唯一约束兜底。
        List<PaymentRecordEntity> existingRecords = paymentRecordMapper.selectByOrderNo(dto.getOrderNo());
        if (!existingRecords.isEmpty()) {
            log.warn("重复支付请求被拒绝: orderNo={}, userId={}, 已有支付记录数={}",
                    dto.getOrderNo(), userId, existingRecords.size());
            throw new BusinessException(ResultCode.ORDER_ALREADY_PAID);
        }

        // 生成支付流水号：PAY + yyyyMMddHHmmss + 4位随机数字
        String paymentNo = generatePaymentNo();

        // 模拟第三方支付：直接置为成功
        String thirdPartyNo = "TXN" + System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        // 创建支付记录
        PaymentRecordEntity entity = new PaymentRecordEntity();
        entity.setPaymentNo(paymentNo);
        entity.setOrderNo(dto.getOrderNo());
        entity.setUserId(userId);
        entity.setAmount(paidAmount);
        entity.setPaymentMethod(dto.getPaymentMethod());
        entity.setStatus(1); // 支付成功
        entity.setThirdPartyNo(thirdPartyNo);
        entity.setPaidAt(now);

        // 幂等防线②（竞态兜底）：两个并发请求可能同时通过防线①，此时靠 payment_record
        // 上的 uk_order_no 唯一约束保证只有一个 insert 成功。应用层「先查后插」无法消除
        // 竞态窗口，数据库唯一约束才是精确一次的最终保证。
        try {
            paymentRecordMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            log.warn("并发重复支付被唯一约束拦截: orderNo={}, userId={}", dto.getOrderNo(), userId);
            throw new BusinessException(ResultCode.ORDER_ALREADY_PAID);
        }

        // 调用订单服务更新订单状态为已支付
        try {
            orderFeignClient.updateOrderPaid(dto.getOrderNo(), dto.getPaymentMethod(), paymentNo);
            log.info("订单状态已更新为已支付: orderNo={}", dto.getOrderNo());
        } catch (Exception e) {
            // 与 fetchPayableAmount 同理：把「当前订单状态不允许支付」等业务拒绝原样透出，
            // 否则用户取消了订单再支付，只会看到含糊的「订单服务调用失败」。
            BusinessException translated = translateBusinessFailure(findFeignException(e));
            if (translated != null) {
                throw translated;
            }
            log.error("调用订单服务更新支付状态失败: orderNo={}", dto.getOrderNo(), e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "订单服务调用失败");
        }

        // 构建响应
        PayResponseDTO response = new PayResponseDTO();
        response.setPaymentId(entity.getId());
        response.setPaymentNo(paymentNo);
        response.setOrderNo(dto.getOrderNo());
        response.setAmount(paidAmount);
        response.setStatus(1);
        response.setStatusLabel(getStatusLabel(1));
        response.setPaidAt(now);

        log.info("支付成功: paymentNo={}, orderNo={}, amount={}, userId={}",
                paymentNo, dto.getOrderNo(), paidAmount, userId);
        return response;
    }

    /**
     * 查询订单应付金额（远程调用订单服务），订单服务同时会校验订单是否属于该用户。
     * <p>
     * 订单服务把业务失败映射为非 2xx 响应（见 GlobalExceptionHandler），Feign 因此抛异常。
     * 这里把响应体里的业务错误码还原出来 —— 否则「无权支付他人订单」会被吞成含糊的
     * "订单服务调用失败"，用户拿到的是错误提示、排查时也看不到真正原因。
     *
     * @param orderNo 订单编号
     * @param userId  支付人用户ID
     * @return 订单应付金额
     */
    private BigDecimal fetchPayableAmount(String orderNo, Long userId) {
        R<BigDecimal> result;
        try {
            result = orderFeignClient.getPayableAmount(orderNo, userId);
        } catch (Exception e) {
            BusinessException translated = translateBusinessFailure(findFeignException(e));
            if (translated != null) {
                throw translated;
            }
            log.error("查询订单应付金额失败: orderNo={}", orderNo, e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "订单服务调用失败");
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在或金额不可用");
        }
        return result.getData();
    }

    /**
     * 从异常的原因链里找出 {@link FeignException}。
     * <p>
     * 不能直接 {@code catch (FeignException e)}：开启 Spring Cloud CircuitBreaker 后，Feign 调用被
     * Resilience4j 包裹，未配置 fallback 时原始异常会被包进 {@code NoFallbackAvailableException}
     * （cause 才是携带响应体的 FeignException）。只 catch FeignException 会漏掉真正的原因，
     * 「无权支付他人订单」这类业务码会被统一吞成「订单服务调用失败」。
     *
     * @param throwable 捕获到的异常
     * @return 原因链中的 FeignException；不存在时返回 null
     */
    private FeignException findFeignException(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof FeignException feignException) {
                return feignException;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    /**
     * 从订单服务的失败响应体中还原业务错误码。
     * <p>
     * 只透传 4xx 里的业务拒绝（订单不存在 / 无权支付 / 参数不合法等）；5xx 或无法解析的响应返回 null，
     * 由调用方统一按「服务不可用」处理 —— 服务端异常不应伪装成客户端错误。
     *
     * @param e Feign 异常（含原始响应体），可为 null
     * @return 还原出的业务异常；无法识别时返回 null
     */
    private BusinessException translateBusinessFailure(FeignException e) {
        if (e == null || e.status() < 400 || e.status() >= 500) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(e.contentUTF8());
            if (node == null) {
                return null;
            }
            JsonNode codeNode = node.get("code");
            if (codeNode == null || !codeNode.isNumber() || codeNode.asInt() == ResultCode.SUCCESS.getCode()) {
                return null;
            }
            String msg = node.path("msg").asText("");
            if (msg.isEmpty()) {
                msg = ResultCode.SERVICE_UNAVAILABLE.getMsg();
            }
            return new BusinessException(codeNode.asInt(), msg);
        } catch (Exception ignore) {
            return null;
        }
    }

    @Override
    public PaymentRecordDTO getByPaymentNo(String paymentNo, Long userId) {
        LambdaQueryWrapper<PaymentRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentRecordEntity::getPaymentNo, paymentNo);
        PaymentRecordEntity entity = paymentRecordMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "支付记录不存在");
        }
        // 归属校验：支付流水只对本人可见，否则任意登录用户都能拿别人的 paymentNo 查到金额与订单号
        if (userId == null || !userId.equals(entity.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该支付记录");
        }
        return toPaymentRecordDTO(entity);
    }

    @Override
    public List<PaymentRecordDTO> getByOrderNo(String orderNo, Long userId) {
        // 按归属过滤而不是直接拒绝：既不泄露「该订单存在支付记录」这一信息，也不会返回他人数据
        return paymentRecordMapper.selectByOrderNo(orderNo).stream()
                .filter(entity -> userId != null && userId.equals(entity.getUserId()))
                .map(this::toPaymentRecordDTO)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<PaymentRecordDTO> adminPageQuery(Integer page, Integer size) {
        Page<PaymentRecordEntity> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PaymentRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(PaymentRecordEntity::getCreatedAt);

        Page<PaymentRecordEntity> result = paymentRecordMapper.selectPage(pageParam, wrapper);

        List<PaymentRecordDTO> list = result.getRecords().stream()
                .map(this::toPaymentRecordDTO)
                .collect(Collectors.toList());

        return new PageResult<>(result.getCurrent(), result.getSize(),
                result.getTotal(), list);
    }

    @Override
    @GlobalTransactional(name = "refund-order", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long id, Long adminUserId) {
        // 查询支付记录
        PaymentRecordEntity entity = paymentRecordMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "支付记录不存在");
        }
        if (entity.getStatus() != 1) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "当前支付状态不允许退款");
        }

        // 更新支付记录状态为已退款
        entity.setStatus(3);
        paymentRecordMapper.updateById(entity);

        // 调用订单服务更新订单为已取消
        try {
            orderFeignClient.updateOrderRefunded(entity.getOrderNo());
            log.info("订单退款已通知: orderNo={}", entity.getOrderNo());
        } catch (Exception e) {
            // 「当前订单状态不允许退款」这类业务拒绝要原样透出，否则管理端只看到含糊的服务不可用，
            // 无法判断是自己点错了订单还是订单服务真的挂了。
            BusinessException translated = translateBusinessFailure(findFeignException(e));
            if (translated != null) {
                throw translated;
            }
            log.error("调用订单服务更新退款状态失败: orderNo={}", entity.getOrderNo(), e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "订单服务调用失败");
        }

        log.info("退款成功: paymentId={}, paymentNo={}, orderNo={}, adminUserId={}",
                id, entity.getPaymentNo(), entity.getOrderNo(), adminUserId);
    }

    /**
     * 生成支付流水号。
     * <p>
     * 格式：PAY + yyyyMMddHHmmss + 4位随机数字
     *
     * @return 支付流水号
     */
    private String generatePaymentNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = new Random().nextInt(9000) + 1000;
        return "PAY" + timestamp + random;
    }

    /**
     * 将实体转换为 DTO。
     *
     * @param entity 支付记录实体
     * @return 支付记录 DTO
     */
    private PaymentRecordDTO toPaymentRecordDTO(PaymentRecordEntity entity) {
        PaymentRecordDTO dto = new PaymentRecordDTO();
        BeanUtils.copyProperties(entity, dto);
        dto.setStatusLabel(getStatusLabel(entity.getStatus()));
        return dto;
    }
}
