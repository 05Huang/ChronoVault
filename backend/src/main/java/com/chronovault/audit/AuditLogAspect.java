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

            // Get IP address
            String ipAddress = getClientIp();

            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .action(action)
                    .icon(auditable.changeType())
                    .ipAddress(ipAddress)
                    .build();
            auditLogRepository.save(auditLog);

            log.debug("Audit log recorded: action={}, user={}, ip={}", action,
                    user != null ? user.getEmail() : "anonymous", ipAddress);
        } catch (Exception e) {
            log.warn("Failed to record audit log: {}", e.getMessage());
        }

        return result;
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
}