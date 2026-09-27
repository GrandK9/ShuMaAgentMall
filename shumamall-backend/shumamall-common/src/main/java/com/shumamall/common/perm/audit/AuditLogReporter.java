package com.shumamall.common.perm.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 审计日志上报器（业务服务侧）。
 * <p>
 * 由 {@code PermissionAspect} 在方法执行结束后调用，把审计记录异步投递给
 * shumamall-permission 服务（唯一持有 MongoDB 审计集合的服务）。
 * <p>
 * <b>为什么是「上报」而不是各服务直连 MongoDB：</b>
 * 挂 {@code @RequirePermission} 的有 product / order / payment / video 四个服务，
 * 直连意味着 4 个服务都要加 MongoDB 驱动 + 连接配置 + 各自建索引，
 * 且审计的查询接口会被拆到四处；集中到权限服务后，业务服务只多了一个
 * 「尽力而为」的上报动作，运维面（索引、保留策略、查询 API）只有一处。
 * <p>
 * <b>为什么用独立线程池而不是 @Async：</b>
 * {@code @Async} 依赖各服务自己 {@code @EnableAsync} 并配置线程池，
 * 漏配时会**静默退化为同步执行**（审计拖慢管理端接口）；这里自带
 * 单线程 + 有界队列的执行器，行为不依赖使用方配置。
 * <p>
 * <b>失败语义（best-effort）：</b>
 * 审计写入失败只打 WARN，绝不抛出——审计不能反向阻断业务。代价是
 * permission 服务不可用期间会丢审计条目；队列满时同样丢弃并计数告警。
 * 这是「可用性优先于审计完整性」的取舍，若后续要求审计不丢，
 * 应改为写本地 MQ（RabbitMQ）再消费落库。
 */
@Slf4j
public class AuditLogReporter {

    /** 权限服务内部审计接口 */
    private static final String AUDIT_PATH = "/api/permission/internal/audit";

    /** 目标服务名 */
    private static final String PERMISSION_SERVICE = "shumamall-permission";

    /** 队列容量：够扛住一次管理端批量操作，超出即丢弃（避免堆积吃内存） */
    private static final int QUEUE_CAPACITY = 1000;

    /** 连接超时（毫秒）——审计不配长超时，访问不到就快速放弃 */
    private static final int CONNECT_TIMEOUT_MS = 1000;

    /** 读超时（毫秒） */
    private static final int READ_TIMEOUT_MS = 2000;

    private final DiscoveryClient discoveryClient;

    /** 专用 RestTemplate：自带超时，避免默认「无限等待」把上报线程永久挂住 */
    private final RestTemplate restTemplate;

    private final ExecutorService executor;

    /** 累计丢弃条数，便于发现「审计静默丢失」 */
    private final AtomicLong droppedCount = new AtomicLong();

    public AuditLogReporter(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(requestFactory);

        // 单线程串行化：审计顺序 = 操作发生顺序，且不与业务线程争抢
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "audit-report");
                    // 守护线程：不阻止 JVM 退出
                    thread.setDaemon(true);
                    return thread;
                });
        this.executor = pool;
    }

    /**
     * 异步上报一条审计记录。
     * <p>
     * 本方法**不会阻塞**且**不会抛异常**，调用方可放心地在业务链路上直接调用。
     *
     * @param dto 审计记录
     */
    public void report(AuditLogDTO dto) {
        if (dto == null) {
            return;
        }
        try {
            executor.execute(() -> doReport(dto));
        } catch (RejectedExecutionException e) {
            long dropped = droppedCount.incrementAndGet();
            log.warn("审计上报队列已满，本条审计被丢弃：action={}, result={}, 累计丢弃={}",
                    dto.getAction(), dto.getResult(), dropped);
        }
    }

    private void doReport(AuditLogDTO dto) {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(PERMISSION_SERVICE);
            if (instances == null || instances.isEmpty()) {
                log.warn("审计上报失败：{} 实例不可用，action={}, result={}",
                        PERMISSION_SERVICE, dto.getAction(), dto.getResult());
                return;
            }
            ServiceInstance instance = instances.get(0);
            String url = "http://" + instance.getHost() + ":" + instance.getPort() + AUDIT_PATH;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(url, new HttpEntity<>(dto, headers), String.class);
            log.debug("审计上报成功：action={}, result={}", dto.getAction(), dto.getResult());
        } catch (Exception e) {
            // 只记消息不打印整个堆栈：审计失败是预期的降级路径，堆栈会淹没日志
            log.warn("审计上报失败（不影响业务）：action={}, result={}, cause={}",
                    dto.getAction(), dto.getResult(), e.getMessage());
        }
    }

    /**
     * 当前累计丢弃条数，供健康检查 / 排查使用。
     */
    public long getDroppedCount() {
        return droppedCount.get();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
