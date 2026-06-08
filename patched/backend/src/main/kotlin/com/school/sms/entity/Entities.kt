package com.school.sms.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@MappedSuperclass
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
    var updatedAt: Instant = Instant.now()
    @PreUpdate fun preUpdate() { updatedAt = Instant.now() }
}

@Entity @Table(name = "roles")
class Role(
    @Column(unique = true, nullable = false) var name: String = "",
    var description: String? = null
) : BaseEntity()

@Entity @Table(name = "permissions")
class Permission(
    @Column(unique = true, nullable = false) var name: String = "",
    var description: String? = null
) : BaseEntity()

@Entity @Table(name = "role_permissions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["role_id", "permission_id"])])
class RolePermission(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "role_id") var role: Role? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "permission_id") var permission: Permission? = null
) : BaseEntity()

@Entity @Table(name = "users",
    indexes = [Index(name = "idx_users_email", columnList = "email"), Index(name = "idx_users_username", columnList = "username")])
class User(
    @Column(unique = true, nullable = false) var username: String = "",
    @Column(unique = true, nullable = false) var email: String = "",
    @Column(nullable = false) var passwordHash: String = "",
    var fullName: String = "",
    var phone: String? = null,
    var profileImagePath: String? = null,
    var enabled: Boolean = true,
    var lastLoginAt: LocalDateTime? = null,
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")])
    var roles: MutableSet<Role> = mutableSetOf()
) : BaseEntity()

@Entity @Table(name = "refresh_tokens", indexes = [Index(name = "idx_rt_token", columnList = "token")])
class RefreshToken(
    @Column(unique = true, nullable = false, length = 512) var token: String = "",
    @ManyToOne(fetch = FetchType.LAZY) var user: User? = null,
    var expiresAt: LocalDateTime = LocalDateTime.now().plusDays(14),
    var revoked: Boolean = false
) : BaseEntity()

@Entity @Table(name = "teachers", indexes = [Index(name = "idx_teacher_user", columnList = "user_id")])
class Teacher(
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") var user: User? = null,
    @Column(unique = true) var employeeCode: String = "",
    var qualification: String? = null,
    var specialization: String? = null,
    var dateOfJoining: LocalDate? = null,
    var salary: BigDecimal = BigDecimal.ZERO
) : BaseEntity()

@Entity @Table(name = "school_classes")
class SchoolClass(
    @Column(unique = true) var name: String = "",
    var section: String = "",
    var academicYear: String = "",
    @ManyToOne(fetch = FetchType.LAZY) var classTeacher: Teacher? = null
) : BaseEntity()

@Entity @Table(name = "subjects")
class Subject(
    @Column(unique = true) var code: String = "",
    var name: String = "",
    @ManyToOne(fetch = FetchType.LAZY) var schoolClass: SchoolClass? = null,
    @ManyToOne(fetch = FetchType.LAZY) var teacher: Teacher? = null
) : BaseEntity()

@Entity @Table(name = "parents")
class Parent(
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") var user: User? = null,
    var occupation: String? = null,
    var address: String? = null
) : BaseEntity()

@Entity @Table(name = "students", indexes = [
    Index(name = "idx_student_admission", columnList = "admissionNo"),
    Index(name = "idx_student_class", columnList = "class_id")
])
class Student(

    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") var user: User? = null,
    @Column(unique = true) var admissionNo: String = "",
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "class_id") var schoolClass: SchoolClass? = null,
    @ManyToOne(fetch = FetchType.LAZY) var parent: Parent? = null,
    var dateOfBirth: LocalDate? = null,
    var gender: String? = null,
    var address: String? = null,

    //added new line
    @OneToMany(mappedBy = "student", cascade = [CascadeType.ALL], orphanRemoval = true)
    var attendances: List<Attendance> = mutableListOf(),


) : BaseEntity()

@Entity @Table(name = "attendance",
    uniqueConstraints = [UniqueConstraint(columnNames = ["student_id", "date"])],
    indexes = [Index(name = "idx_att_date", columnList = "date")])
class Attendance(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id") var student: Student? = null,
    var date: LocalDate = LocalDate.now(),
    @Enumerated(EnumType.STRING) var status: AttendanceStatus = AttendanceStatus.PRESENT,
    var remarks: String? = null
) : BaseEntity()

enum class AttendanceStatus { PRESENT, ABSENT, LATE, EXCUSED }

@Entity @Table(name = "exams")
class Exam(
    var name: String = "",
    var academicYear: String = "",
    var startDate: LocalDate? = null,
    var endDate: LocalDate? = null
) : BaseEntity()

@Entity @Table(name = "marks",
    uniqueConstraints = [UniqueConstraint(columnNames = ["exam_id", "student_id", "subject_id"])])
class Mark(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "exam_id") var exam: Exam? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id") var student: Student? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "subject_id") var subject: Subject? = null,
    var marksObtained: BigDecimal = BigDecimal.ZERO,
    var maxMarks: BigDecimal = BigDecimal(100),
    var grade: String? = null
) : BaseEntity()

@Entity @Table(name = "fees", indexes = [Index(name = "idx_fee_student", columnList = "student_id")])
class Fee(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id") var student: Student? = null,
    var term: String = "",
    var amount: BigDecimal = BigDecimal.ZERO,
    var amountPaid: BigDecimal = BigDecimal.ZERO,
    var dueDate: LocalDate? = null,
    @Enumerated(EnumType.STRING) var status: FeeStatus = FeeStatus.PENDING
) : BaseEntity()

enum class FeeStatus { PENDING, PARTIAL, PAID, OVERDUE }

@Entity @Table(name = "fee_payments")
class FeePayment(
    @ManyToOne(fetch = FetchType.LAZY) var fee: Fee? = null,
    var amount: BigDecimal = BigDecimal.ZERO,
    var paidOn: LocalDate = LocalDate.now(),
    var method: String = "CASH",
    var reference: String? = null
) : BaseEntity()

@Entity @Table(name = "assignments")
class Assignment(
    var title: String = "",
    @Column(columnDefinition = "TEXT") var description: String? = null,
    @ManyToOne(fetch = FetchType.LAZY) var subject: Subject? = null,
    @ManyToOne(fetch = FetchType.LAZY) var teacher: Teacher? = null,
    var dueDate: LocalDate? = null,
    var filePath: String? = null
) : BaseEntity()

@Entity @Table(name = "assignment_submissions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["assignment_id", "student_id"])])
class AssignmentSubmission(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assignment_id") var assignment: Assignment? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id") var student: Student? = null,
    var filePath: String? = null,
    var submittedAt: LocalDateTime = LocalDateTime.now(),
    var grade: String? = null,
    var feedback: String? = null
) : BaseEntity()

@Entity @Table(name = "books")
class Book(
    @Column(unique = true) var isbn: String = "",
    var title: String = "",
    var author: String? = null,
    var totalCopies: Int = 1,
    var availableCopies: Int = 1
) : BaseEntity()

@Entity @Table(name = "book_issues")
class BookIssue(
    @ManyToOne(fetch = FetchType.LAZY) var book: Book? = null,
    @ManyToOne(fetch = FetchType.LAZY) var student: Student? = null,
    var issuedOn: LocalDate = LocalDate.now(),
    var dueOn: LocalDate = LocalDate.now().plusDays(14),
    var returnedOn: LocalDate? = null
) : BaseEntity()

@Entity @Table(name = "notices")
class Notice(
    var title: String = "",
    @Column(columnDefinition = "TEXT") var content: String = "",
    var targetRole: String? = null,
    var publishedOn: LocalDate = LocalDate.now()
) : BaseEntity()

@Entity @Table(name = "file_metadata", indexes = [Index(name = "idx_file_owner", columnList = "ownerType,ownerId")])
class FileMetadata(
    var ownerType: String = "",
    var ownerId: Long = 0,
    var category: String = "",
    var originalName: String = "",
    var storedPath: String = "",
    var contentType: String? = null,
    var size: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY) var uploadedBy: User? = null
) : BaseEntity()

@Entity @Table(name = "audit_logs", indexes = [Index(name = "idx_audit_user", columnList = "userId,createdAt")])
class AuditLog(
    var userId: Long? = null,
    var username: String? = null,
    var action: String = "",
    var entity: String? = null,
    var entityId: String? = null,
    @Column(columnDefinition = "TEXT") var details: String? = null,
    var ip: String? = null,
    var success: Boolean = true
) : BaseEntity()

@Entity @Table(name = "password_reset_tokens", indexes = [Index(name = "idx_prt_token", columnList = "token")])
class PasswordResetToken(
    @Column(unique = true, nullable = false, length = 512) var token: String = "",
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") var user: User? = null,
    var expiresAt: LocalDateTime = LocalDateTime.now().plusHours(1),
    var used: Boolean = false
) : BaseEntity()
