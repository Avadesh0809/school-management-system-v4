package com.school.sms.exception

import com.school.sms.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

class NotFoundException(msg: String) : RuntimeException(msg)
class BadRequestException(msg: String) : RuntimeException(msg)
class ConflictException(msg: String) : RuntimeException(msg)
class UnauthorizedException(msg: String) : RuntimeException(msg)
class ForbiddenException(msg: String) : RuntimeException(msg)

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(e.message ?: "Not found"))
    @ExceptionHandler(BadRequestException::class)
    fun badRequest(e: BadRequestException) = ResponseEntity.badRequest().body(ApiResponse.fail(e.message ?: "Bad request"))
    @ExceptionHandler(ConflictException::class)
    fun conflict(e: ConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(e.message ?: "Conflict"))
    @ExceptionHandler(UnauthorizedException::class, BadCredentialsException::class)
    fun unauth(e: Exception) = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(e.message ?: "Unauthorized"))
    @ExceptionHandler(AccessDeniedException::class)
    fun forbidden(e: AccessDeniedException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("Access denied"))
    @ExceptionHandler(ForbiddenException::class)
    fun forbiddenCustom(e: ForbiddenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(e.message ?: "Forbidden"))
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun dataIntegrity(e: DataIntegrityViolationException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Data integrity violation: {}", e.message)
        val msg = e.mostSpecificCause.message ?: e.message ?: ""
        val friendly = when {
            msg.contains("foreign key", ignoreCase = true) ->
                "Cannot complete operation: this record is referenced by other data"
            msg.contains("unique", ignoreCase = true) || msg.contains("duplicate", ignoreCase = true) ->
                "A record with the same value already exists"
            else -> "Data integrity violation: cannot perform this operation"
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(friendly))
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun upload(e: MaxUploadSizeExceededException) = ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.fail("File too large"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val errors = e.bindingResult.allErrors.associate { (it as? FieldError)?.field.orEmpty() to (it.defaultMessage ?: "invalid") }
        return ResponseEntity.badRequest().body(ApiResponse.fail("Validation failed", errors))
    }

    @ExceptionHandler(Exception::class)
    fun generic(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unhandled error", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("Internal server error: ${e.message}"))
    }
}
