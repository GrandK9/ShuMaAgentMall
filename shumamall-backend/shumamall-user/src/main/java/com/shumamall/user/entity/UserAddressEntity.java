package com.shumamall.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.shumamall.common.crypto.EncryptTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收货地址实体。
 */
@Data
@TableName(value = "user_address", autoResultMap = true)
public class UserAddressEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 收货人姓名（AES-256-CBC + 随机 IV 加密存储） */
    @TableField(value = "receiver", typeHandler = EncryptTypeHandler.class)
    private String consignee;

    /** 手机号（AES-256-CBC + 随机 IV 加密存储） */
    @TableField(value = "phone", typeHandler = EncryptTypeHandler.class)
    private String phone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区 */
    private String district;

    /** 详细地址（AES-256-CBC 加密存储：能定位到具体门牌的信息与姓名/手机号同等敏感） */
    @TableField(value = "detail", typeHandler = EncryptTypeHandler.class)
    private String detailAddress;

    /** 是否默认地址 0-否 1-是 */
    private Integer isDefault;

    /** 标签（如：家、公司） */
    private String label;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
