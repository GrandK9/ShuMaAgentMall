package com.shumamall.agent.dto;

import lombok.Data;

/**
 * 收货地址 DTO（Feign 调用 user 服务后精简字段）。
 */
@Data
public class AddressInfoDTO {

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

    /** 是否默认地址 0-否 1-是 */
    private Integer isDefault;
}
