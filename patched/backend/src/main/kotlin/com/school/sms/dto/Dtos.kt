package com.school.sms.dto

import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class ApiResponse<T>(val success: Boolean, val message: String? = null, val data: T? = null, val errors: Any? = null) {
    companion object {
        fun <T> ok(data: T? = null, message: String? = null) = ApiResponse(true, message, data)
        fun fail(message: String, errors: Any? = null) = ApiResponse<Nothing>(false, message, null, errors)
    }
}

data class PagedResponse<T>(val items: List<T>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int)

data class LoginRequest(@field:NotBlank val username: String = "", @field:NotBlank val password: String = "")
data class LoginResponse(val accessToken: String, val refreshToken: String, val user: UserDto)
data class RefreshRequest(@field:NotBlank val refreshToken: String = "")
data class ChangePasswordRequest(@field:NotBlank val oldPassword: String = "", @field:NotBlank @field:Size(min=6) val newPassword: String = "")
data class UpdateProfileRequest(
    @field:Size(min=3, max=50) val username: String? = null,
    @field:Email val email: String? = null,
    @field:Size(max=150) val fullName: String? = null,
    @field:Size(max=20) val phone: String? = null
)

data class ForgotPasswordRequest(@field:NotBlank @field:Email val email: String = "")
data class ResetPasswordRequest(
    @field:NotBlank val token: String = "",
    @field:NotBlank @field:Size(min=6, max=100) val newPassword: String = ""
)

data class UserDto(val id: Long?, val username: String, val email: String, val fullName: String, val phone: String?, val profileImagePath: String?, val enabled: Boolean, val roles: List<String>)
data class CreateUserRequest(
    @field:NotBlank @field:Size(min=3, max=50) val username: String = "",
    @field:NotBlank @field:Email val email: String = "",
    @field:NotBlank @field:Size(min=6, max=100) val password: String = "",
    @field:NotBlank @field:Size(max=150) val fullName: String = "",
    @field:Size(max=20) val phone: String? = null,
    val enabled: Boolean = true,
    val roles: Set<String> = emptySet()
)
data class UpdateUserRequest(
    @field:Email val email: String? = null,
    @field:Size(max=150) val fullName: String? = null,
    @field:Size(max=20) val phone: String? = null,
    val enabled: Boolean? = null,
    val roles: Set<String>? = null
)

data class RoleDto(val id: Long?, val name: String, val description: String?, val permissions: List<String> = emptyList())
data class PermissionDto(val id: Long?, val name: String, val description: String?)
data class AssignPermissionsRequest(@field:NotEmpty val permissions: Set<String> = emptySet())

data class TeacherDto(
    val id: Long?, val userId: Long?, val fullName: String?, val email: String?, val phone: String?,
    val employeeCode: String, val qualification: String?, val specialization: String?,
    val dateOfJoining: LocalDate?, val salary: BigDecimal
)
data class CreateTeacherRequest(
    val userId: Long? = null,
    val fullName: String? = null,
    @field:Email val email: String? = null,
    val phone: String? = null,
    val password: String? = null,
    @field:NotBlank val employeeCode: String = "",
    val qualification: String? = null,
    val specialization: String? = null,
    val dateOfJoining: LocalDate? = null,
    @field:DecimalMin("0.0") val salary: BigDecimal = BigDecimal.ZERO
)
data class UpdateTeacherRequest(
    val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val employeeCode: String? = null,
    val qualification: String? = null,
    val specialization: String? = null,
    val dateOfJoining: LocalDate? = null,
    @field:DecimalMin("0.0") val salary: BigDecimal? = null
)

data class StudentDto(
    val id: Long?, val userId: Long?, val fullName: String?, val email: String?, val phone: String?,
    val admissionNo: String, val classId: Long?, val className: String?,
    val parentId: Long?, val parentName: String?,
    val dateOfBirth: LocalDate?, val gender: String?, val address: String?
)
data class CreateStudentRequest(
    val userId: Long? = null,
    val fullName: String? = null,
    @field:Email val email: String? = null,
    val phone: String? = null,
    val password: String? = null,
    @field:NotBlank val admissionNo: String = "",
    val classId: Long? = null,
    val parentId: Long? = null,
    val dateOfBirth: LocalDate? = null,
    val gender: String? = null,
    val address: String? = null
)
data class UpdateStudentRequest(
    val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val admissionNo: String? = null,
    val classId: Long? = null,
    val parentId: Long? = null,
    val dateOfBirth: LocalDate? = null,
    val gender: String? = null,
    val address: String? = null
)

data class ParentDto(
    val id: Long?, val userId: Long?, val fullName: String?, val email: String?, val phone: String?,
    val occupation: String?, val address: String?
)
data class CreateParentRequest(
    val userId: Long? = null,
    val fullName: String? = null,
    @field:Email val email: String? = null,
    val phone: String? = null,
    val password: String? = null,
    val occupation: String? = null,
    val address: String? = null
)
data class UpdateParentRequest(
    val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val occupation: String? = null,
    val address: String? = null
)

data class SchoolClassDto(
    val id: Long?,
    @field:NotBlank val name: String = "",
    @field:NotBlank val section: String = "",
    @field:NotBlank val academicYear: String = "",
    val classTeacherId: Long?,
    val classTeacherName: String? = null
)
data class UpdateClassRequest(
    val name: String? = null,
    val section: String? = null,
    val academicYear: String? = null,
    val classTeacherId: Long? = null
)

data class SubjectDto(
    val id: Long?,
    @field:NotBlank val code: String = "",
    @field:NotBlank val name: String = "",
    val classId: Long?,
    val className: String? = null,
    val teacherId: Long?,
    val teacherName: String? = null
)
data class UpdateSubjectRequest(
    val code: String? = null,
    val name: String? = null,
    val classId: Long? = null,
    val teacherId: Long? = null
)

data class AttendanceDto(
    val id: Long?,
    @field:NotNull val studentId: Long,
    val studentName: String? = null,
    @field:NotNull val date: LocalDate,
    @field:NotBlank val status: String,
    val remarks: String? = null
)
data class MarkAttendanceRequest(@field:NotEmpty val records: List<AttendanceDto> = emptyList())

data class ExamDto(
    val id: Long?,
    @field:NotBlank val name: String = "",
    @field:NotBlank val academicYear: String = "",
    val startDate: LocalDate?,
    val endDate: LocalDate?
)
data class UpdateExamRequest(
    val name: String? = null,
    val academicYear: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
)

data class MarkDto(
    val id: Long?,
    @field:NotNull val examId: Long,
    val examName: String? = null,
    @field:NotNull val studentId: Long,
    val studentName: String? = null,
    @field:NotNull val subjectId: Long,
    val subjectName: String? = null,
    @field:NotNull @field:DecimalMin("0.0") val marksObtained: BigDecimal,
    @field:NotNull @field:DecimalMin("0.0") val maxMarks: BigDecimal,
    val grade: String? = null
)

data class FeeDto(
    val id: Long?,
    @field:NotNull val studentId: Long,
    val studentName: String? = null,
    @field:NotBlank val term: String = "",
    @field:NotNull @field:DecimalMin("0.0") val amount: BigDecimal,
    @field:DecimalMin("0.0") val amountPaid: BigDecimal = BigDecimal.ZERO,
    val dueDate: LocalDate?,
    val status: String = "PENDING"
)
data class UpdateFeeRequest(
    val term: String? = null,
    @field:DecimalMin("0.0") val amount: BigDecimal? = null,
    val dueDate: LocalDate? = null,
    val status: String? = null
)
data class PayFeeRequest(
    @field:NotNull val feeId: Long? = null,
    @field:NotNull @field:DecimalMin("0.01") val amount: BigDecimal = BigDecimal.ZERO,
    val method: String = "CASH",
    val reference: String? = null
)

data class AssignmentDto(
    val id: Long?,
    @field:NotBlank val title: String = "",
    val description: String? = null,
    val subjectId: Long? = null,
    val subjectName: String? = null,
    val teacherId: Long? = null,
    val teacherName: String? = null,
    val dueDate: LocalDate? = null,
    val filePath: String? = null
)
data class UpdateAssignmentRequest(
    val title: String? = null,
    val description: String? = null,
    val subjectId: Long? = null,
    val teacherId: Long? = null,
    val dueDate: LocalDate? = null
)
data class SubmissionDto(val id: Long?, val assignmentId: Long, val studentId: Long, val filePath: String?, val submittedAt: LocalDateTime, val grade: String?, val feedback: String?)
data class GradeSubmissionRequest(val grade: String? = null, val feedback: String? = null)

data class BookDto(val id: Long?, val isbn: String, val title: String, val author: String?, val totalCopies: Int, val availableCopies: Int)
data class IssueBookRequest(@field:NotNull val bookId: Long? = null, @field:NotNull val studentId: Long? = null)

data class NoticeDto(
    val id: Long?,
    @field:NotBlank val title: String = "",
    @field:NotBlank val content: String = "",
    val targetRole: String? = null,
    val publishedOn: LocalDate? = null
)
data class UpdateNoticeRequest(
    val title: String? = null,
    val content: String? = null,
    val targetRole: String? = null,
    val publishedOn: LocalDate? = null
)

data class FileMetadataDto(val id: Long?, val ownerType: String, val ownerId: Long, val category: String, val originalName: String, val storedPath: String, val contentType: String?, val size: Long)

data class AuditLogDto(val id: Long?, val username: String?, val action: String, val entity: String?, val entityId: String?, val ip: String?, val success: Boolean, val createdAt: java.time.Instant)
