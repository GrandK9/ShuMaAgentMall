package com.shumamall.admin.controller;

import com.shumamall.admin.dto.DashboardVO;
import com.shumamall.admin.service.DashboardService;
import com.shumamall.common.result.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台仪表盘控制器。
 * <p>
 * 聚合各微服务统计数据，供前端首页展示。
 *
 * @author ShuMaMall Team
 */
@Slf4j
@Tag(name = "管理端-仪表盘", description = "聚合各微服务统计数据，提供管理后台首页仪表盘数据")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取仪表盘聚合数据。
     *
     * @return 仪表盘视图对象
     */
    @GetMapping
    public R<DashboardVO> getDashboard() {
        log.info("Fetching admin dashboard data");
        DashboardVO dashboard = dashboardService.getDashboard();
        return R.ok(dashboard);
    }
}
