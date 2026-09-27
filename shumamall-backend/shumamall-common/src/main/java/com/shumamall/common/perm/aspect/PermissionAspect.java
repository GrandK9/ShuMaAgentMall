package com.shumamall.common.perm.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.perm.annotation.RequirePermission;
import com.shumamall.common.perm.audit.AuditLogDTO;
import com.shumamall.common.perm.audit.AuditLogReporter;
import com.shumamall.common.perm.audit.AuditResult;
import com.shumamall.common.result.ResultCode;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 权限校验 + 操作审计 AOP 切面。
 * <p>
 * 环绕标注了 {@link RequirePermission} 的方法：**执行前**校验当前用户的权限，
 * **执行后**无论成功、失败还是被拒绝，都上报一条审计记录（见 {@link AuditLogReporter}）。
 * <p>
 * 从 {@code @Before} 改为 {@code @Around} 的原因：审计必须记录「这次操作到底执行成功没有」，
 * 前置通知拿不到方法返回值，也无法区分「校验通过后业务抛异常」与「校验未通过」——
 * 这两类在审计里的含义完全不同（前者是被允许但失败的操作，后者是越权尝试）。
 * <p>
 * 由于 common 模块不引入 Feign 依赖的完整 Web 环境，这里通过
 * {@link RestTemplate} + {@link DiscoveryClient} 直接调用 permission 服务的内部接口。
 * <p>
 * <b>审计失败不影响业务</b>：上报是异步 + best-effort，见 {@link AuditLogReporter}。
 */
@Slf4j
@Aspect
public class PermissionAspect {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 入参 JSON 的最大留存长度，避免大 body（如批量导入）把审计文档撑爆 */
    private static final int PARAMS_MAX_LENGTH = 500;

    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;
    private final AuditLogReporter auditLogReporter;

    public PermissionAspect(DiscoveryClient discoveryClient, RestTemplate restTemplate,
                            AuditLogReporter auditLogReporter) {
        this.discoveryClient = discoveryClient;
        this.restTemplate = restTemplate;
        this.auditLogReporter = auditLogReporter;
    }

    /**
     * 环绕通知：校验权限 → 执行目标方法 → 上报审计。
     *
     * @param joinPoint 连接点
     * @return 目标方法的返回值
     * @throws Throwable 校验失败抛 {@link BusinessException}；执行异常原样抛出
     */
    @Around("@annotation(com.shumamall.common.perm.annotation.RequirePermission)")
    public Object checkPermissionAndAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        if (annotation == null) {
            return joinPoint.proceed();
        }

        String[] requiredPermissions = annotation.value();
        long startMs = System.currentTimeMillis();

        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            log.warn("权限校验失败：当前无用户上下文");
            audit(joinPoint, null, requiredPermissions, AuditResult.FORBIDDEN, startMs);
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 从 permission 服务获取用户权限
        Set<String> userPermissions = getUserPermissions(userId);
        if (userPermissions == null || userPermissions.isEmpty()) {
            log.warn("权限校验失败：userId={} 没有任何权限", userId);
            audit(joinPoint, userId, requiredPermissions, AuditResult.FORBIDDEN, startMs);
            throw new BusinessException(ResultCode.PERMISSION_DENIED);
        }

        RequirePermission.Logical logical = annotation.logical();
        boolean hasPermission;
        if (logical == RequirePermission.Logical.AND) {
            hasPermission = Arrays.stream(requiredPermissions)
                    .allMatch(userPermissions::contains);
        } else {
            hasPermission = Arrays.stream(requiredPermissions)
                    .anyMatch(userPermissions::contains);
        }

        if (!hasPermission) {
            log.warn("权限校验失败：userId={}, required={}, logical={}, userPermissions={}",
                    userId, Arrays.toString(requiredPermissions), logical, userPermissions);
            audit(joinPoint, userId, requiredPermissions, AuditResult.FORBIDDEN, startMs);
            throw new BusinessException(ResultCode.PERMISSION_DENIED);
        }

        log.debug("权限校验通过：userId={}, required={}", userId, Arrays.toString(requiredPermissions));

        try {
            Object result = joinPoint.proceed();
            audit(joinPoint, userId, requiredPermissions, AuditResult.SUCCESS, startMs);
            return result;
        } catch (Throwable t) {
            // 权限已通过但业务失败（状态机不允许、参数非法等），与「越权」区分记录
            audit(joinPoint, userId, requiredPermissions, AuditResult.FAILED, startMs);
            throw t;
        }
    }

    /**
     * 组装并异步上报一条审计记录。
     * <p>
     * 本方法自身也吞掉所有异常：审计是旁路能力，任何构造失败都不应把已经执行完的业务操作变成失败。
     */
    private void audit(JoinPoint joinPoint, Long userId, String[] actions, String result, long startMs) {
        if (auditLogReporter == null) {
            return;
        }
        try {
            AuditLogDTO dto = new AuditLogDTO();
            dto.setAdminUserId(userId);
            dto.setAction(String.join(",", actions));
            dto.setResult(result);
            dto.setOccurredAtEpochMilli(startMs);
            dto.setCostMs(System.currentTimeMillis() - startMs);

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                dto.setResource(request.getRequestURI());
                dto.setMethod(request.getMethod());
                dto.setIp(resolveClientIp(request));
            }
            dto.setParams(serializeArgs(joinPoint.getArgs()));

            auditLogReporter.report(dto);
        } catch (Exception e) {
            log.warn("组装审计记录失败（不影响业务）: {}", e.getMessage());
        }
    }

    /**
     * 提取客户端 IP。
     * <p>
     * 经网关转发后 remoteAddr 是网关地址，真实来源在 X-Forwarded-For 的第一段。
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 序列化方法入参用于审计。
     * <p>
     * 剔除无法/不应序列化的参数：Servlet 请求响应对象、文件流、MultipartFile。
     * 序列化结果截断到 {@link #PARAMS_MAX_LENGTH}，避免大对象写满审计文档。
     */
    private static String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> loggable = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg == null || arg instanceof ServletRequest || arg instanceof ServletResponse
                    || arg instanceof MultipartFile || arg instanceof InputStream || arg instanceof OutputStream) {
                continue;
            }
            loggable.add(arg);
        }
        if (loggable.isEmpty()) {
            return null;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(loggable);
            return json.length() > PARAMS_MAX_LENGTH
                    ? json.substring(0, PARAMS_MAX_LENGTH) + "...(truncated)"
                    : json;
        } catch (Exception e) {
            // 参数里带循环引用或自定义序列化异常时，不能因此丢掉整条审计
            log.debug("审计参数序列化失败: {}", e.getMessage());
            return "[" + loggable.size() + " args, serialization failed]";
        }
    }

    /**
     * 通过 RestTemplate 调用 permission 服务获取用户权限。
     */
    private Set<String> getUserPermissions(Long userId) {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances("shumamall-permission");
            if (instances == null || instances.isEmpty()) {
                log.warn("permission 服务不可用，跳过权限校验");
                return Collections.emptySet();
            }
            ServiceInstance instance = instances.get(0);
            String url = "http://" + instance.getHost() + ":" + instance.getPort()
                    + "/api/permission/internal/getUserPermissions?userId=" + userId;

            @SuppressWarnings("unchecked")
            Set<String> permissions = restTemplate.getForObject(url, Set.class);
            return permissions != null ? permissions : Collections.emptySet();
        } catch (Exception e) {
            log.warn("调用 permission 服务获取权限失败: {}", e.getMessage());
            return Collections.emptySet();
        }
    }
}
