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

    @Around("execution(* com.finance.graphqlapi.controller.GraphQLQueryController.create*(..)) || execution(* com.finance.graphqlapi.controller.GraphQLQueryController.update*(..)) || execution(* com.finance.graphqlapi.controller.GraphQLQueryController.delete*(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        String method = pjp.getSignature().getName();
        String action = method.startsWith("create") ? "CREATE" : method.startsWith("update") ? "UPDATE" : "DELETE";
        HttpServletRequest request = null;
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) request = servlet.getRequest();
        auditService.log(action, method, null, null, result, request);
        return result;
    }
}
