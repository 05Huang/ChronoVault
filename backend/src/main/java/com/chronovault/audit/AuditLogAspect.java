package com.chronovault.audit;

import com.chronovault.entity.AuditLog;
import com.chronovault.entity.User;
import com.chronovault.repository.AuditLogRepository;
import com.chronovault.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Around("@annotation(com.chronovault.audit.Auditable)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Auditable auditable = method.getAnnotation(Auditable.class);

            String action = auditable.action().isEmpty()
                    ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
                    : auditable.action();

            // Get current user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = null;
            if (auth != null && auth.getName() != null) {
                user = userRepository.findByEmail(auth.getName()).orElse(null);
            }

            // Get IP address and user agent
            String ipAddress = getClientIp();
            String userAgent = getUserAgent();

            // Extract resource type from annotation
            String resourceType = auditable.resourceType().isEmpty() ? null : auditable.resourceType();

            // Extract resource ID from result or method parameters
            Long resourceId = extractResourceId(auditable.resourceId(), result, joinPoint, signature);

            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(action)
                    .changeType(auditable.changeType())
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .ipAddress(ipAddress)
                    .build();
            auditLogRepository.save(auditLog);

            log.debug("Audit log recorded: action={}, resource={}:{}, user={}, ip={}",
                    action, resourceType, resourceId,
                    user != null ? user.getEmail() : "anonymous", ipAddress);
        } catch (Exception e) {
            log.warn("Failed to record audit log: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Extract resource ID from method result or parameters.
     * Supports: "#result.id" for return value, "#paramName" for method parameters.
     */
    private Long extractResourceId(String resourceIdExpr, Object result,
                                    ProceedingJoinPoint joinPoint, MethodSignature signature) {
        if (resourceIdExpr == null || resourceIdExpr.isEmpty()) return null;

        try {
            if ("#result.id".equals(resourceIdExpr) && result != null) {
                // Extract id from result object via reflection
                var getIdMethod = result.getClass().getMethod("id");
                if (getIdMethod == null) getIdMethod = result.getClass().getMethod("getId");
                if (getIdMethod != null) {
                    Object id = getIdMethod.invoke(result);
                    if (id instanceof Long l) return l;
                    if (id instanceof Number n) return n.longValue();
                }
            } else if (resourceIdExpr.startsWith("#")) {
                String paramName = resourceIdExpr.substring(1);
                String[] paramNames = signature.getParameterNames();
                Object[] args = joinPoint.getArgs();
                for (int i = 0; i < paramNames.length; i++) {
                    if (paramName.equals(paramNames[i]) && args[i] instanceof Long l) {
                        return l;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract resource ID from '{}': {}", resourceIdExpr, e.getMessage());
        }
        return null;
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    return xRealIp;
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Failed to get client IP: {}", e.getMessage());
        }
        return "unknown";
    }

    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("Failed to get user agent: {}", e.getMessage());
        }
        return null;
    }
}