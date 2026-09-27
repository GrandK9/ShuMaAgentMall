package com.shumamall.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 收货地址 DTO（请求/响应通用）。
 */
@Data
public class AddressDTO {

    private Long id;

    /** 收货人姓名 */
    @NotBlank(message = "收货人姓名不能为空")
    private String consignee;

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 省 */
    @NotBlank(message = "省份不能为空")
    private String province;

    /** 市 */
    @NotBlank(message = "城市不能为空")
    private String city;

    /** 区 */
    @NotBlank(message = "区县不能为空")
    private String district;

    /** 详细地址 */
    @NotBlank(message = "详细地址不能为空")
    private String detailAddress;

    /** 是否默认地址 0-否 1-是 */
    private Integer isDefault;

    /** 标签（如：家、公司） */
    private String label;
}
