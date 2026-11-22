package com.finance.backend.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditMutationAspect {
    private final AuditService auditService;
    public AuditMutationAspect(AuditService auditService){this.auditService=auditService;}

    @Around("execution(* com.finance.internal.controller.InternalApiController.create*(..))"
            + " || execution(* com.finance.internal.controller.InternalApiController.update*(..))"
            + " || execution(* com.finance.internal.controller.InternalApiController.delete*(..))"
            + " || execution(* com.finance.internal.controller.InternalApiController.approve*(..))"
            + " || execution(* com.finance.internal.controller.InternalApiController.reject*(..))"
            + " || execution(* com.finance.internal.controller.InternalApiController.generate*(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        String method = pjp.getSignature().getName();
        String action = method.startsWith("create") ? "CREATE"
                : method.startsWith("update") ? "UPDATE"
                : method.startsWith("delete") ? "DELETE"
                : method.startsWith("approve") ? "APPROVE"
                : method.startsWith("reject") ? "REJECT" : "GENERATE";
        HttpServletRequest request = null;
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) request = servlet.getRequest();
        auditService.log(action, method, null, null, result, request);
        return result;
    }
}