package com.school.sms.repository

import com.school.sms.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    fun findByUsername(username: String): Optional<User>
    fun findByEmail(email: String): Optional<User>
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}

@Repository
interface RoleRepository : JpaRepository<Role, Long> {
    fun findByName(name: String): Optional<Role>
}

@Repository
interface PermissionRepository : JpaRepository<Permission, Long> {
    fun findByName(name: String): Optional<Permission>
}

@Repository
interface RolePermissionRepository : JpaRepository<RolePermission, Long> {
    fun findByRoleId(roleId: Long): List<RolePermission>
}

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByToken(token: String): Optional<RefreshToken>
    fun deleteByUserId(userId: Long)
}

@Repository
interface TeacherRepository : JpaRepository<Teacher, Long>, JpaSpecificationExecutor<Teacher> {
    fun findByUserId(userId: Long): Optional<Teacher>
    fun findByEmployeeCode(code: String): Optional<Teacher>
}

@Repository
interface StudentRepository : JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {
    fun findByUserId(userId: Long): Optional<Student>
    fun findByAdmissionNo(no: String): Optional<Student>
    fun findBySchoolClassId(classId: Long): List<Student>
    fun findByParentId(parentId: Long): List<Student>
}

@Repository
interface ParentRepository : JpaRepository<Parent, Long>, JpaSpecificationExecutor<Parent> {
    fun findByUserId(userId: Long): Optional<Parent>
}

@Repository
interface SchoolClassRepository : JpaRepository<SchoolClass, Long>, JpaSpecificationExecutor<SchoolClass>

@Repository
interface SubjectRepository : JpaRepository<Subject, Long>, JpaSpecificationExecutor<Subject> {
    fun findBySchoolClassId(classId: Long): List<Subject>
    fun findByTeacherId(teacherId: Long): List<Subject>
}

@Repository
interface AttendanceRepository : JpaRepository<Attendance, Long>, JpaSpecificationExecutor<Attendance> {
    fun findByStudentId(studentId: Long): List<Attendance>
    fun findByStudentIdAndDateBetween(studentId: Long, from: LocalDate, to: LocalDate): List<Attendance>
    fun findByDate(date: LocalDate): List<Attendance>
    fun findByStudentIdAndDate(studentId: Long, date: LocalDate): java.util.Optional<Attendance>

    @Query("select count(a) from Attendance a where a.student.id=:sid and a.status='PRESENT'")
    fun countPresent(sid: Long): Long

    @Query("select count(a) from Attendance a where a.student.id=:sid")
    fun countTotal(sid: Long): Long
}

@Repository
interface ExamRepository : JpaRepository<Exam, Long>, JpaSpecificationExecutor<Exam>

@Repository
interface MarkRepository : JpaRepository<Mark, Long>, JpaSpecificationExecutor<Mark> {
    fun findByStudentIdAndExamId(studentId: Long, examId: Long): List<Mark>
    fun findByExamIdAndStudentIdAndSubjectId(examId: Long, studentId: Long, subjectId: Long): java.util.Optional<Mark>
    fun findByStudentId(studentId: Long): List<Mark>
    fun findByExamId(examId: Long): List<Mark>
}

@Repository
interface FeeRepository : JpaRepository<Fee, Long>, JpaSpecificationExecutor<Fee> {
    fun findByStudentId(studentId: Long): List<Fee>
    fun findByStatus(status: FeeStatus): List<Fee>
}

@Repository
interface FeePaymentRepository : JpaRepository<FeePayment, Long> {
    fun findByFeeId(feeId: Long): List<FeePayment>
}

@Repository
interface AssignmentRepository : JpaRepository<Assignment, Long>, JpaSpecificationExecutor<Assignment> {
    fun findByTeacherId(teacherId: Long): List<Assignment>
    fun findBySubjectId(subjectId: Long): List<Assignment>
}

@Repository
interface AssignmentSubmissionRepository : JpaRepository<AssignmentSubmission, Long> {
    fun findByAssignmentId(assignmentId: Long): List<AssignmentSubmission>
    fun findByAssignmentIdAndStudentId(aid: Long, sid: Long): Optional<AssignmentSubmission>
    fun findByStudentId(studentId: Long): List<AssignmentSubmission>
}

@Repository
interface BookRepository : JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {
    fun findByIsbn(isbn: String): Optional<Book>
}

@Repository
interface BookIssueRepository : JpaRepository<BookIssue, Long> {
    fun findByStudentIdAndReturnedOnIsNull(studentId: Long): List<BookIssue>
    fun findByBookIdAndReturnedOnIsNull(bookId: Long): List<BookIssue>
}

@Repository
interface NoticeRepository : JpaRepository<Notice, Long>, JpaSpecificationExecutor<Notice>

@Repository
interface FileMetadataRepository : JpaRepository<FileMetadata, Long> {
    fun findByOwnerTypeAndOwnerId(ownerType: String, ownerId: Long): List<FileMetadata>
}

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog>

@Repository
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {
    fun findByToken(token: String): Optional<PasswordResetToken>
}
