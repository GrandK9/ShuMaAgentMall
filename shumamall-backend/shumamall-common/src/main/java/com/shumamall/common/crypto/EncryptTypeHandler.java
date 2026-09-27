package com.shumamall.common.crypto;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 敏感字段加解密 TypeHandler：写入自动加密、读取自动解密，对业务代码透明。
 * <p>
 * 使用方式（Entity 字段）：
 * <pre>
 * &#64;TableName(value = "user", autoResultMap = true)   // 查询解密必须开启 autoResultMap
 * public class UserEntity {
 *     &#64;TableField(value = "phone", typeHandler = EncryptTypeHandler.class)
 *     private String phone;
 * }
 * </pre>
 * <p>
 * 密钥来自 {@link CryptoKeyHolder}（启动时由 {@link CryptoConfig} 注入）；未配置密钥时明文透传。
 */
@MappedTypes(String.class)
public class EncryptTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, CryptoKeyHolder.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return CryptoKeyHolder.decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return CryptoKeyHolder.decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return CryptoKeyHolder.decrypt(cs.getString(columnIndex));
    }
}
