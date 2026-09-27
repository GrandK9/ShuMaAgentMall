package com.shumamall.order.service;

import com.shumamall.common.result.PageResult;
import com.shumamall.order.dto.OrderCreateDTO;
import com.shumamall.order.dto.OrderDTO;
import com.shumamall.order.dto.OrderPageQuery;
import com.shumamall.order.dto.OrderStatisticsVO;
import com.shumamall.order.dto.SalesTrendItemVO;
import com.shumamall.order.dto.TopProductVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单服务接口。
 */
public interface OrderService {

    /**
     * 创建订单。
     *
     * @param userId 用户ID
     * @param dto    创建订单参数
     * @return 订单DTO
     */
    OrderDTO createOrder(Long userId, OrderCreateDTO dto);

    /**
     * 创建秒杀订单（异步建单，供秒杀 MQ 消费者与对账重投调用）。
     * <p>
     * 与 {@link #createOrder} 的差别只有两点：单价取秒杀价、<b>不挂 Seata 全局事务</b>。
     * 秒杀库存在入口已由 Redis Lua 原子预扣挡住超卖，数据库扣减只是兜底；
     * 用户请求线程也不再等待建单结果，没有跨服务强一致的诉求，不该为此引入全局事务的开销。
     *
     * @param userId       用户ID
     * @param skuId        SKU ID
     * @param quantity     数量
     * @param seckillPrice 秒杀价（成交价）
     * @param addressId    收货地址ID
     * @param remark       订单备注
     * @return 订单DTO
     */
    OrderDTO createSeckillOrder(Long userId, Long skuId, Integer quantity, BigDecimal seckillPrice,
                                Long addressId, String remark);

    /**
     * 根据订单ID查询详情。
     *
     * @param id     订单ID
     * @param userId 用户ID（用于校验归属）
     * @return 订单DTO
     */
    OrderDTO getById(Long id, Long userId);

    /**
     * 分页查询我的订单。
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageResult<OrderDTO> pageQuery(OrderPageQuery query);

    /**
     * 用户取消订单。
     *
     * @param id     订单ID
     * @param userId 用户ID
     */
    void cancelOrder(Long id, Long userId);

    /**
     * 用户确认收货（待收货 → 已完成）。
     *
     * @param id     订单ID
     * @param userId 用户ID（用于校验归属）
     */
    void receiveOrder(Long id, Long userId);

    /**
     * 管理端分页查询所有订单。
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageResult<OrderDTO> adminPageQuery(OrderPageQuery query);

    /**
     * 管理端查询订单详情。
     *
     * @param id 订单ID
     * @return 订单DTO
     */
    OrderDTO adminGetDetail(Long id);

    /**
     * 管理端更新订单状态。
     * <p>
     * 只允许两条链路推进：待发货→待收货（发货）、待收货→已完成（确认收货）。
     * 其余迁移一律拒绝 —— 取消要走 {@link #cancelOrder} / 退款回调，否则库存与销量不会回补。
     *
     * @param id     订单ID
     * @param status 目标状态
     * @param remark 备注（可选）
     */
    void updateStatus(Long id, Integer status, String remark);

    /**
     * 超时取消订单（定时任务调用）。
     *
     * @param orderId 订单ID
     */
    void timeoutCancel(Long orderId);

    /**
     * 支付成功回调——更新订单状态为已支付（待发货）。
     *
     * @param orderNo       订单编号
     * @param paymentMethod 支付方式 1-微信 2-支付宝
     * @param paymentNo     支付流水号
     */
    void updateOrderPaid(String orderNo, Integer paymentMethod, String paymentNo);

    /**
     * 退款回调——更新订单状态为已取消。
     *
     * @param orderNo 订单编号
     */
    void updateOrderRefunded(String orderNo);

    /**
     * 查询订单应付金额（供支付服务做支付金额强校验）。
     *
     * @param orderNo 订单编号
     * @param userId  支付人用户ID（用于校验订单归属，禁止替他人订单付款）
     * @return 订单应付金额
     */
    BigDecimal getPayableAmount(String orderNo, Long userId);

    /**
     * 订单统计（供管理端仪表盘聚合调用）。
     *
     * @return 订单统计（总订单数 / 今日订单数 / 今日销售额）
     */
    OrderStatisticsVO getStatistics();

    /**
     * 近 N 天销售趋势（按支付时间逐日聚合已支付订单）。
     *
     * @param days 天数（含今天）
     * @return 按日期升序的趋势项列表
     */
    List<SalesTrendItemVO> getSalesTrend(int days);

    /**
     * 热销商品排行（按已支付订单明细聚合销售额，降序取 Top N）。
     *
     * @param limit 返回条数
     * @return 热销商品列表
     */
    List<TopProductVO> getTopProducts(int limit);
}
