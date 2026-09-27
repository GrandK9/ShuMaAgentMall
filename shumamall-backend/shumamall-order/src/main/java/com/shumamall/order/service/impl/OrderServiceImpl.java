package com.shumamall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.common.dto.AddressSnapshotDTO;
import com.shumamall.common.dto.SkuInfoDTO;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import com.shumamall.common.auth.SecurityContext;
import com.shumamall.order.api.ProductFeignClient;
import com.shumamall.order.api.UserFeignClient;
import com.shumamall.order.dao.CartMapper;
import com.shumamall.order.dao.OrderItemMapper;
import com.shumamall.order.dao.OrderMapper;
import com.shumamall.order.dto.OrderCreateDTO;
import com.shumamall.order.dto.OrderDTO;
import com.shumamall.order.dto.OrderItemDTO;
import com.shumamall.order.dto.OrderPageQuery;
import com.shumamall.order.dto.OrderStatisticsVO;
import com.shumamall.order.dto.SalesTrendItemVO;
import com.shumamall.order.dto.TopProductVO;
import com.shumamall.order.entity.CartEntity;
import com.shumamall.order.entity.OrderEntity;
import com.shumamall.order.entity.OrderItemEntity;
import com.shumamall.order.enums.OrderStatusEnum;
import com.shumamall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 订单服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductFeignClient productFeignClient;
    private final UserFeignClient userFeignClient;
    private final CartMapper cartMapper;
    private final ObjectMapper objectMapper;

    private static final Random RANDOM = new Random();
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public OrderDTO createOrder(Long userId, OrderCreateDTO dto) {
        String orderNo = generateOrderNo();
        BigDecimal freightAmount = new BigDecimal("0.00");
        List<OrderItemEntity> items = new ArrayList<>();

        if (dto.getCartItemIds() != null && !dto.getCartItemIds().isEmpty()) {
            // ========== 购物车多 SKU 结算 ==========
            List<CartEntity> cartItems = cartMapper.selectBatchIds(dto.getCartItemIds());
            if (cartItems == null || cartItems.isEmpty()) {
                throw new BusinessException(ResultCode.NOT_FOUND, "购物车项不存在");
            }
            // 校验所有购物车项归属当前用户
            for (CartEntity cart : cartItems) {
                if (!cart.getUserId().equals(userId)) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "无权操作购物车项: " + cart.getId());
                }
            }

            // 批量查询 SKU 信息
            List<Long> skuIds = cartItems.stream().map(CartEntity::getSkuId).collect(Collectors.toList());
            Map<Long, SkuInfoDTO> skuInfoMap;
            try {
                R<List<SkuInfoDTO>> batchResult = productFeignClient.getSkuListByIds(skuIds);
                if (batchResult == null || !batchResult.isSuccess() || batchResult.getData() == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "SKU信息批量查询失败");
                }
                skuInfoMap = batchResult.getData().stream()
                        .collect(Collectors.toMap(SkuInfoDTO::getSkuId, s -> s));
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("批量查询SKU信息失败: skuIds={}", skuIds, e);
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "商品服务调用失败");
            }

            BigDecimal totalAmount = BigDecimal.ZERO;
            for (CartEntity cart : cartItems) {
                SkuInfoDTO skuInfo = skuInfoMap.get(cart.getSkuId());
                if (skuInfo == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "SKU信息不存在: " + cart.getSkuId());
                }
                // 购物车行的数量兜底校验：脏数据（0/负数）会让订单金额为负，且扣库存时反向加库存
                if (cart.getQuantity() == null || cart.getQuantity() < 1) {
                    throw new BusinessException(ResultCode.PARAM_INVALID,
                            "购物车商品数量非法: skuId=" + cart.getSkuId());
                }
                OrderItemEntity item = new OrderItemEntity();
                item.setOrderNo(orderNo);
                item.setProductId(skuInfo.getProductId());
                item.setProductName(skuInfo.getProductName());
                item.setSkuId(cart.getSkuId());
                item.setSkuSpecs(skuInfo.getSkuSpecs());
                item.setProductImage(skuInfo.getProductImage());
                item.setQuantity(cart.getQuantity());
                item.setPrice(skuInfo.getPrice());
                item.setSubtotal(skuInfo.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
                items.add(item);
                totalAmount = totalAmount.add(item.getSubtotal());
            }

            OrderEntity order = new OrderEntity();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setTotalAmount(totalAmount);
            order.setPayAmount(totalAmount.add(freightAmount));
            order.setFreightAmount(freightAmount);
            order.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());
            order.setRemark(dto.getRemark());
            orderMapper.insert(order);

            for (OrderItemEntity item : items) {
                item.setOrderId(order.getId());
                orderItemMapper.insert(item);
            }

            // 批量扣减库存
            for (CartEntity cart : cartItems) {
                try {
                    productFeignClient.deductStock(cart.getSkuId(), cart.getQuantity());
                } catch (Exception e) {
                    log.error("扣减库存失败: skuId={}, quantity={}", cart.getSkuId(), cart.getQuantity(), e);
                    throw new BusinessException(ResultCode.STOCK_INSUFFICIENT, "库存不足: " + cart.getSkuId());
                }
            }

            // 删除已购买的购物车项
            cartMapper.deleteBatchIds(dto.getCartItemIds());

            // 填充地址快照
            fillAddressSnapshot(order, dto.getAddressId(), userId);

            log.info("购物车结算下单成功: orderNo={}, userId={}, amount={}, itemCount={}",
                    orderNo, userId, totalAmount, items.size());
            return getById(order.getId(), userId);

        } else {
            // ========== 直接购买单 SKU ==========
            SkuInfoDTO skuInfo;
            try {
                R<SkuInfoDTO> result = productFeignClient.getSkuById(dto.getSkuId());
                if (result == null || !result.isSuccess() || result.getData() == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "SKU信息查询失败");
                }
                skuInfo = result.getData();
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("查询SKU信息失败: skuId={}", dto.getSkuId(), e);
                throw new BusinessException(ResultCode.NOT_FOUND, "SKU信息查询失败");
            }

            // 创建订单明细
            OrderItemEntity item = new OrderItemEntity();
            item.setOrderNo(orderNo);
            item.setProductId(skuInfo.getProductId());
            item.setProductName(skuInfo.getProductName() != null ? skuInfo.getProductName() : dto.getProductName());
            item.setSkuId(dto.getSkuId());
            item.setSkuSpecs(skuInfo.getSkuSpecs());
            item.setProductImage(skuInfo.getProductImage());
            item.setQuantity(dto.getQuantity());
            item.setPrice(skuInfo.getPrice());
            item.setSubtotal(skuInfo.getPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));
            items.add(item);

            BigDecimal totalAmount = item.getSubtotal();
            OrderEntity order = new OrderEntity();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setTotalAmount(totalAmount);
            order.setPayAmount(totalAmount.add(freightAmount));
            order.setFreightAmount(freightAmount);
            order.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());
            order.setRemark(dto.getRemark());
            orderMapper.insert(order);

            item.setOrderId(order.getId());
            orderItemMapper.insert(item);

            // 扣减库存
            try {
                productFeignClient.deductStock(dto.getSkuId(), dto.getQuantity());
            } catch (Exception e) {
                log.error("扣减库存失败: skuId={}, quantity={}", dto.getSkuId(), dto.getQuantity(), e);
                throw new BusinessException(ResultCode.STOCK_INSUFFICIENT, "库存不足");
            }

            // 填充地址快照
            fillAddressSnapshot(order, dto.getAddressId(), userId);

            log.info("订单创建成功: orderNo={}, userId={}, amount={}", orderNo, userId, totalAmount);
            return getById(order.getId(), userId);
        }
    }

    @Override
    public OrderDTO getById(Long id, Long userId) {
        OrderEntity order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在");
        }
        // 校验归属
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该订单");
        }
        return toOrderDTO(order);
    }

    @Override
    public PageResult<OrderDTO> pageQuery(OrderPageQuery query) {
        Page<OrderEntity> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();

        if (query.getUserId() != null) {
            wrapper.eq(OrderEntity::getUserId, query.getUserId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(OrderEntity::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(OrderEntity::getCreatedAt);

        Page<OrderEntity> result = orderMapper.selectPage(page, wrapper);

        List<OrderDTO> list = result.getRecords().stream()
                .map(this::toOrderDTO)
                .collect(Collectors.toList());

        return new PageResult<>(result.getCurrent(), result.getSize(),
                result.getTotal(), list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id, Long userId) {
        OrderEntity order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该订单");
        }
        if (order.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getCode()) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "当前订单状态不允许取消");
        }

        // 条件更新（CAS）：并发双击取消、或用户取消与 60 秒定时任务同时命中同一笔订单时，
        // 只有一方能把状态从「待付款」改走，另一方影响行数为 0 直接失败，
        // 从而避免两边都执行库存回补导致库存虚增。
        if (!casUpdateStatus(order.getId(), OrderStatusEnum.PENDING_PAYMENT, OrderStatusEnum.CANCELLED,
                update -> update.set(OrderEntity::getCancelReason, "用户主动取消"))) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "订单状态已变更，请刷新后重试");
        }

        restoreStockForOrder(order, "用户取消");

        log.info("订单已取消: orderNo={}, userId={}", order.getOrderNo(), userId);
    }

    @Override
    public PageResult<OrderDTO> adminPageQuery(OrderPageQuery query) {
        Page<OrderEntity> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();

        if (query.getStatus() != null) {
            wrapper.eq(OrderEntity::getStatus, query.getStatus());
        }
        if (query.getUserId() != null) {
            wrapper.eq(OrderEntity::getUserId, query.getUserId());
        }
        wrapper.orderByDesc(OrderEntity::getCreatedAt);

        Page<OrderEntity> result = orderMapper.selectPage(page, wrapper);

        List<OrderDTO> list = result.getRecords().stream()
                .map(this::toOrderDTO)
                .collect(Collectors.toList());

        return new PageResult<>(result.getCurrent(), result.getSize(),
                result.getTotal(), list);
    }

    @Override
    public OrderDTO adminGetDetail(Long id) {
        OrderEntity order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在");
        }
        return toOrderDTO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status, String remark) {
        OrderEntity order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在");
        }

        OrderStatusEnum targetStatus = OrderStatusEnum.of(status);
        if (targetStatus == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "无效的订单状态");
        }

        OrderStatusEnum currentStatus = OrderStatusEnum.of(order.getStatus());
        // 合法迁移链路定义在 OrderStatusEnum.ALLOWED_TRANSITIONS
        if (currentStatus == null || !currentStatus.canTransitionTo(targetStatus)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID,
                    String.format("不允许把订单从「%s」改为「%s」",
                            currentStatus == null ? order.getStatus() : currentStatus.getLabel(),
                            targetStatus.getLabel()));
        }

        // 条件更新（CAS）：两个管理员同时操作时只让一方生效
        LambdaUpdateWrapper<OrderEntity> update = new LambdaUpdateWrapper<>();
        Consumer<LambdaUpdateWrapper<OrderEntity>> customize = null;
        if (targetStatus == OrderStatusEnum.PENDING_RECEIVE) {
            // 发货：待发货 -> 待收货，记录发货时间
            customize = u -> u.set(OrderEntity::getDeliveryTime, LocalDateTime.now());
        } else if (targetStatus == OrderStatusEnum.COMPLETED) {
            // 确认收货：待收货 -> 已完成，记录收货时间
            customize = u -> u.set(OrderEntity::getReceiveTime, LocalDateTime.now());
        }
        if (!casUpdateStatus(order.getId(), currentStatus, targetStatus, customize)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "订单状态已变更，请刷新后重试");
        }

        log.info("订单状态更新: orderNo={}, {} -> {}, remark={}",
                order.getOrderNo(), order.getStatus(), status, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveOrder(Long id, Long userId) {
        OrderEntity order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该订单");
        }
        if (order.getStatus() != OrderStatusEnum.PENDING_RECEIVE.getCode()) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "当前订单状态不允许确认收货");
        }

        // 条件更新（CAS）：与定时任务、管理端代确认收货互斥，避免重复记录收货时间
        if (!casUpdateStatus(order.getId(), OrderStatusEnum.PENDING_RECEIVE, OrderStatusEnum.COMPLETED,
                update -> update.set(OrderEntity::getReceiveTime, LocalDateTime.now()))) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "订单状态已变更，请刷新后重试");
        }

        log.info("用户确认收货: orderNo={}, userId={}", order.getOrderNo(), userId);
    }

    /**
     * 条件更新订单状态（CAS），把「期望的原状态」写进 where 条件。
     * <p>
     * 「先查出状态、判断、再 updateById」在并发下两个请求都能通过判断，于是同一笔订单会被处理两次
     * （库存回补两次、销量累加两次）。改成条件更新后由 InnoDB 行锁串行化：影响行数为 0
     * 即说明订单已被并发请求改走，调用方据此抛业务异常或跳过。
     *
     * @param orderId   订单主键
     * @param expected  期望的原状态
     * @param target    目标状态
     * @param customize 额外要 set 的字段（如取消原因、发货时间），可为 null
     * @return 是否更新成功
     */
    private boolean casUpdateStatus(Long orderId, OrderStatusEnum expected, OrderStatusEnum target,
                                    Consumer<LambdaUpdateWrapper<OrderEntity>> customize) {
        LambdaUpdateWrapper<OrderEntity> update = new LambdaUpdateWrapper<OrderEntity>()
                .eq(OrderEntity::getId, orderId)
                .eq(OrderEntity::getStatus, expected.getCode())
                .set(OrderEntity::getStatus, target.getCode());
        if (customize != null) {
            customize.accept(update);
        }
        return orderMapper.update(null, update) > 0;
    }

    /**
     * 回补订单内所有 SKU 的库存。
     * <p>
     * 逐项独立 try-catch：单个 SKU 回补失败不影响其余项。这里不回滚也不重试，因为远程调用不参与
     * 本地事务 —— 抛异常只会回滚本地状态更新，而已经回补成功的 SKU 并不会撤销，反而留下
     * 「订单还是原状态、库存却已加回」的更差数据。因此失败落 error 日志，留人工核对入口。
     *
     * @param order 订单
     * @param scene 场景描述，用于日志定位（用户取消 / 超时取消 / 退款回补）
     */
    private void restoreStockForOrder(OrderEntity order, String scene) {
        List<OrderItemEntity> items = orderItemMapper.selectByOrderId(order.getId());
        for (OrderItemEntity item : items) {
            try {
                productFeignClient.restoreStock(item.getSkuId(), item.getQuantity());
            } catch (Exception e) {
                log.error("{}回补库存失败: orderNo={}, skuId={}, quantity={}",
                        scene, order.getOrderNo(), item.getSkuId(), item.getQuantity(), e);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void timeoutCancel(Long orderId) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("超时取消订单不存在: orderId={}", orderId);
            return;
        }
        if (order.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getCode()) {
            return;
        }

        // 条件更新（CAS）：用户可能正在同时手动取消，这里抢不到就跳过，避免重复回补库存
        if (!casUpdateStatus(order.getId(), OrderStatusEnum.PENDING_PAYMENT, OrderStatusEnum.CANCELLED,
                update -> update.set(OrderEntity::getCancelReason, "超时未支付，系统自动取消"))) {
            log.info("超时取消跳过（订单状态已被并发处理）: orderNo={}", order.getOrderNo());
            return;
        }

        restoreStockForOrder(order, "超时取消");

        log.info("超时订单已取消: orderNo={}", order.getOrderNo());
    }

    /**
     * 定时任务：每60秒扫描超时未支付订单并自动取消。
     */
    @Scheduled(fixedRate = 60000)
    @Transactional(rollbackFor = Exception.class)
    public void scheduledTimeoutCancel() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(30);
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getStatus, OrderStatusEnum.PENDING_PAYMENT.getCode())
                .lt(OrderEntity::getCreatedAt, deadline);

        List<OrderEntity> timeoutOrders = orderMapper.selectList(wrapper);
        for (OrderEntity order : timeoutOrders) {
            try {
                timeoutCancel(order.getId());
            } catch (Exception e) {
                log.error("定时取消订单失败: orderNo={}", order.getOrderNo(), e);
            }
        }

        if (!timeoutOrders.isEmpty()) {
            log.info("定时任务：已取消 {} 笔超时订单", timeoutOrders.size());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderPaid(String orderNo, Integer paymentMethod, String paymentNo) {
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderEntity::getOrderNo, orderNo);
        OrderEntity order = orderMapper.selectOne(wrapper);

        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        // 条件更新（CAS）：把「状态为待付款」从"先查后判"改为"更新条件"。
        // 原实现「查出 → 判断状态 → updateById」存在竞态：两个并发请求可同时读到待付款、
        // 同时通过判断，导致订单被重复置为已支付、商品销量被重复累加。
        // 改为条件更新后由 InnoDB 行锁串行化，只有一个请求能命中状态条件，
        // 影响行数为 0 即说明订单已被并发请求处理（或本就不处于待付款）。
        LambdaUpdateWrapper<OrderEntity> update = new LambdaUpdateWrapper<OrderEntity>()
                .eq(OrderEntity::getId, order.getId())
                .eq(OrderEntity::getStatus, OrderStatusEnum.PENDING_PAYMENT.getCode())
                .set(OrderEntity::getStatus, OrderStatusEnum.PENDING_DELIVERY.getCode())
                .set(OrderEntity::getPaymentMethod, paymentMethod)
                .set(OrderEntity::getPaymentNo, paymentNo)
                .set(OrderEntity::getPaymentTime, LocalDateTime.now());

        if (orderMapper.update(null, update) == 0) {
            log.warn("订单支付状态并发冲突: orderNo={}, 查询时状态={}", orderNo, order.getStatus());
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "当前订单状态不允许支付");
        }

        // 支付成功回写商品销量（支撑热门商品排序；失败仅告警，不影响支付主流程）
        syncSalesVolume(order);

        log.info("订单支付成功: orderNo={}, paymentMethod={}, paymentNo={}", orderNo, paymentMethod, paymentNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderRefunded(String orderNo) {
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderEntity::getOrderNo, orderNo);
        OrderEntity order = orderMapper.selectOne(wrapper);

        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != OrderStatusEnum.PENDING_DELIVERY.getCode()) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "当前订单状态不允许退款");
        }

        // 条件更新（CAS）：两个管理员同时点退款时只让一方生效，否则库存会被回补两次、销量倒退两次
        if (!casUpdateStatus(order.getId(), OrderStatusEnum.PENDING_DELIVERY, OrderStatusEnum.CANCELLED,
                update -> update.set(OrderEntity::getCancelReason, "管理员退款"))) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "订单状态已变更，请刷新后重试");
        }

        // 退款补偿链：库存回补 + 销量负向回退
        // 支付成功时库存已扣减、销量已 +n，退款必须对冲，否则库存永久流失、销量虚高影响热门排序
        compensateAfterRefund(order);

        log.info("订单已退款取消: orderNo={}", orderNo);
    }

    /**
     * 退款补偿：回补 SKU 库存并负向回退商品销量。
     * <p>
     * 逐项独立 try-catch 的策略与 {@link #restoreStockForOrder} 一致，失败落错误日志待人工核对。
     *
     * @param order 已退款订单
     */
    private void compensateAfterRefund(OrderEntity order) {
        restoreStockForOrder(order, "退款");
        adjustSalesVolume(order, -1);
    }

    @Override
    public BigDecimal getPayableAmount(String orderNo, Long userId) {
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderEntity::getOrderNo, orderNo);
        OrderEntity order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        // 归属校验：支付人必须是订单所有者。否则 A 用户能替 B 用户的订单付款，
        // 付完款订单仍归属 B，而支付记录记在 A 名下，形成「订单-支付」归属不一致的脏数据。
        if (userId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "缺少支付人标识");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权支付他人订单");
        }
        return order.getPayAmount();
    }

    /**
     * 支付成功后回写商品销量（按商品聚合订单明细数量）。
     * <p>
     * 支撑热门商品排序数据；销量回写失败仅告警，不影响支付主流程与事务。
     *
     * @param order 已支付订单
     */
    private void syncSalesVolume(OrderEntity order) {
        adjustSalesVolume(order, 1);
    }

    /**
     * 调整商品销量：按商品聚合订单明细数量后调用商品服务自增。
     *
     * @param order 订单
     * @param sign  +1 支付成功累加，-1 退款回退
     */
    private void adjustSalesVolume(OrderEntity order, int sign) {
        try {
            List<OrderItemEntity> items = orderItemMapper.selectByOrderId(order.getId());
            // 同一商品可能有多行明细（不同 SKU），按商品聚合购买数量
            Map<Long, Integer> quantities = items.stream()
                    .filter(i -> i.getProductId() != null && i.getQuantity() != null)
                    .collect(Collectors.groupingBy(OrderItemEntity::getProductId,
                            Collectors.summingInt(OrderItemEntity::getQuantity)));
            quantities.forEach((productId, qty) -> {
                try {
                    productFeignClient.updateSalesVolume(productId, sign * qty);
                } catch (Exception e) {
                    log.warn("回写商品销量失败: orderNo={}, productId={}, delta={}",
                            order.getOrderNo(), productId, sign * qty, e);
                }
            });
        } catch (Exception e) {
            log.warn("销量回写异常（忽略）: orderNo={}", order.getOrderNo(), e);
        }
    }

    @Override
    public OrderStatisticsVO getStatistics() {
        // 总订单数
        Long totalOrders = orderMapper.selectCount(null);

        // 今日订单数：今日创建的订单
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LambdaQueryWrapper<OrderEntity> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(OrderEntity::getCreatedAt, todayStart);
        Long todayOrders = orderMapper.selectCount(todayWrapper);

        // 今日销售额：今日已支付订单（待发货/待收货/已完成/售后中）的实付金额之和
        LambdaQueryWrapper<OrderEntity> paidWrapper = new LambdaQueryWrapper<>();
        paidWrapper.ge(OrderEntity::getPaymentTime, todayStart)
                .in(OrderEntity::getStatus, 1, 2, 3, 5);
        BigDecimal todaySales = orderMapper.selectList(paidWrapper).stream()
                .map(OrderEntity::getPayAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrderStatisticsVO(totalOrders, todayOrders, todaySales);
    }

    @Override
    public List<SalesTrendItemVO> getSalesTrend(int days) {
        int dayCount = Math.max(days, 1);
        // 仅统计已支付订单（待发货/待收货/已完成/售后中），按支付时间归属日期
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(OrderEntity::getPaymentTime, LocalDate.now().minusDays(dayCount - 1L).atStartOfDay())
                .in(OrderEntity::getStatus, 1, 2, 3, 5);
        List<OrderEntity> paidOrders = orderMapper.selectList(wrapper);

        Map<LocalDate, List<OrderEntity>> byDate = paidOrders.stream()
                .filter(o -> o.getPaymentTime() != null)
                .collect(Collectors.groupingBy(o -> o.getPaymentTime().toLocalDate()));

        // 补齐无销售日期（返回连续 N 天，前端折线图友好）
        List<SalesTrendItemVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = dayCount - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<OrderEntity> dayOrders = byDate.getOrDefault(date, Collections.emptyList());
            BigDecimal amount = dayOrders.stream()
                    .map(OrderEntity::getPayAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new SalesTrendItemVO(date, (long) dayOrders.size(), amount));
        }
        return result;
    }

    @Override
    public List<TopProductVO> getTopProducts(int limit) {
        return orderItemMapper.selectTopProducts(Math.max(limit, 1));
    }

    // ==================== 私有方法 ====================

    /**
     * 填充订单地址快照。
     * <p>
     * 调用用户服务查询地址信息，序列化为 JSON 存入订单的 address_snapshot 字段。
     * 快照方式确保历史订单的地址不受用户后续地址变更影响。</p>
     *
     * @param order     订单实体（已插入数据库，含 ID）
     * @param addressId 地址ID
     * @param userId    用户ID
     */
    private void fillAddressSnapshot(OrderEntity order, Long addressId, Long userId) {
        try {
            R<AddressSnapshotDTO> addrResult = userFeignClient.getAddressById(addressId, userId);
            if (addrResult != null && addrResult.isSuccess() && addrResult.getData() != null) {
                String snapshotJson = objectMapper.writeValueAsString(addrResult.getData());
                order.setAddressSnapshot(snapshotJson);
                orderMapper.updateById(order);
                log.debug("地址快照已填充: orderNo={}, addressId={}", order.getOrderNo(), addressId);
            } else {
                log.warn("地址快照获取结果为空: addressId={}, userId={}", addressId, userId);
            }
        } catch (JsonProcessingException e) {
            log.warn("地址快照序列化失败: addressId={}", addressId, e);
        } catch (Exception e) {
            log.warn("获取地址快照失败: addressId={}", addressId, e);
        }
    }

    /**
     * 生成订单号，格式：ORD + yyyyMMddHHmmss + 4位随机数字。
     *
     * @return 订单号
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(ORDER_NO_FORMATTER);
        int randomDigits = RANDOM.nextInt(10000);
        return "ORD" + timestamp + String.format("%04d", randomDigits);
    }

    /**
     * 将订单实体转换为DTO。
     *
     * @param order 订单实体
     * @return 订单DTO
     */
    private OrderDTO toOrderDTO(OrderEntity order) {
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPayAmount(order.getPayAmount());
        dto.setFreightAmount(order.getFreightAmount());
        dto.setStatus(order.getStatus());
        dto.setStatusLabel(OrderStatusEnum.getLabel(order.getStatus()));
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setPaymentNo(order.getPaymentNo());
        dto.setPaymentTime(order.getPaymentTime());
        dto.setDeliveryTime(order.getDeliveryTime());
        dto.setReceiveTime(order.getReceiveTime());
        dto.setRemark(order.getRemark());
        dto.setAddressSnapshot(order.getAddressSnapshot());
        dto.setCancelReason(order.getCancelReason());
        dto.setCreatedAt(order.getCreatedAt());

        // 查询订单明细
        List<OrderItemEntity> items = orderItemMapper.selectByOrderId(order.getId());
        if (items != null) {
            dto.setItems(items.stream().map(this::toOrderItemDTO).collect(Collectors.toList()));
        } else {
            dto.setItems(Collections.emptyList());
        }

        return dto;
    }

    /**
     * 将订单明细实体转换为DTO。
     *
     * @param item 订单明细实体
     * @return 订单明细DTO
     */
    private OrderItemDTO toOrderItemDTO(OrderItemEntity item) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setId(item.getId());
        dto.setSkuId(item.getSkuId());
        dto.setProductId(item.getProductId());
        dto.setProductName(item.getProductName());
        dto.setSkuSpecs(item.getSkuSpecs());
        dto.setProductImage(item.getProductImage());
        dto.setPrice(item.getPrice());
        dto.setQuantity(item.getQuantity());
        dto.setSubtotal(item.getSubtotal());
        return dto;
    }
}
