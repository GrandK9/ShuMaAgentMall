package com.shumamall.common.dto;

import lombok.Data;

/**
 * 地址快照 DTO（Feign 内部调用及 JSON 序列化用）。
 * <p>
 * 下单时从用户服务获取地址信息，序列化为 JSON 存储在订单的 address_snapshot 字段中，
 * 防止后续地址变更影响历史订单数据。</p>
 */
@Data
public class AddressSnapshotDTO {

    /** 地址ID */
    private Long id;

    /** 收货人姓名 */
    private String consignee;

    /** 手机号 */
    private String phone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区 */
    private String district;

    /** 详细地址 */
    private String detailAddress;

    /** 标签（如：家、公司） */
    private String label;
}
