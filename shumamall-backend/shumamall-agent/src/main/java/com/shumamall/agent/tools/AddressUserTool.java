package com.shumamall.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.dto.AddressInfoDTO;
import com.shumamall.agent.dto.UserInfoDTO;
import com.shumamall.agent.feign.UserFeignClient;
import com.shumamall.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户/地址工具：查询当前用户个人信息与收货地址（调用 shumamall-user 用户端接口）。
 * <p>
 * 身份来源为 {@link ToolContext} 中的 userId（编排器注入），Feign 透传 JWT，
 * user 服务的 TokenFilter 从 token 解析 userId，两侧保持一致。
 */
@Slf4j
@Component
public class AddressUserTool extends BaseTool {

    private final UserFeignClient userFeignClient;
    private final ObjectMapper objectMapper;

    public AddressUserTool(UserFeignClient userFeignClient, ObjectMapper objectMapper) {
        this.userFeignClient = userFeignClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询当前用户收货地址列表（下单前选地址用）。
     */
    @Tool(description = "查询当前用户的收货地址列表（含默认地址标记），下单前应先查地址")
    public String getAddressList(ToolContext toolContext) {
        Long userId = userId(toolContext);
        try {
            R<List<AddressInfoDTO>> resp = userFeignClient.addressList();
            if (!resp.isSuccess() || resp.getData() == null || resp.getData().isEmpty()) {
                return "{\"empty\":true}";
            }
            return objectMapper.writeValueAsString(resp.getData());
        } catch (Exception e) {
            log.error("查询地址列表失败: userId={}", userId, e);
            return error(e);
        }
    }

    /**
     * 查询当前用户个人信息。
     */
    @Tool(description = "查询当前用户的个人信息（昵称、手机号、邮箱等）")
    public String getUserInfo(ToolContext toolContext) {
        Long userId = userId(toolContext);
        try {
            R<UserInfoDTO> resp = userFeignClient.userInfo();
            if (!resp.isSuccess() || resp.getData() == null) {
                return "{\"empty\":true}";
            }
            return objectMapper.writeValueAsString(resp.getData());
        } catch (Exception e) {
            log.error("查询用户信息失败: userId={}", userId, e);
            return error(e);
        }
    }
}
