package com.school.sms.audit

import com.school.sms.entity.AuditLog
import com.school.sms.repository.AuditLogRepository
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.RUNTIME)
annotation class Auditable(val action: String, val entity: String = "")

@Service
class AuditService(private val repo: AuditLogRepository) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(action: String, entity: String?, entityId: String?, details: String?, success: Boolean = true) {
        val auth = SecurityContextHolder.getContext().authentication
        val username = auth?.name
        val req = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        repo.save(AuditLog(
            username = username, action = action, entity = entity, entityId = entityId,
            details = details, ip = req?.remoteAddr, success = success
        ))
    }
}

@Aspect @Component
class AuditAspect(private val auditService: AuditService) {
    @Around("@annotation(com.school.sms.audit.Auditable)")
    fun around(pjp: ProceedingJoinPoint): Any? {
        val method = (pjp.signature as MethodSignature).method
        val ann = method.getAnnotation(Auditable::class.java)
        return try {
            val r = pjp.proceed()
            auditService.record(ann.action, ann.entity.ifBlank { null }, null, "args=${pjp.args.joinToString()}", true)
            r
        } catch (e: Throwable) {
            auditService.record(ann.action, ann.entity.ifBlank { null }, null, "error=${e.message}", false)
            throw e
        }
    }
}
