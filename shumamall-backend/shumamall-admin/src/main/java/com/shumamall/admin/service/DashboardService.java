package com.shumamall.admin.service;

import com.shumamall.admin.dto.DashboardVO;

/**
 * 仪表盘服务接口。
 *
 * @author ShuMaMall Team
 */
public interface DashboardService {

    /**
     * 获取仪表盘聚合数据。
     * <p>
     * 通过 Feign 调用各微服务统计接口，若某个服务不可用则对应字段返回 0。
     *
     * @return 仪表盘视图对象
     */
    DashboardVO getDashboard();
}
