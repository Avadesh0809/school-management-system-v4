package com.school.sms.controller

import com.school.sms.audit.Auditable
import com.school.sms.dto.*
import com.school.sms.files.FileStorageService
import com.school.sms.files.toDto
import com.school.sms.service.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

@RestController
@RequestMapping("/api/auth")
class AuthController(private val auth: AuthService, private val userService: UserService, private val storage: FileStorageService) {

    @PostMapping("/login")
    @Auditable(action = "LOGIN", entity = "User")
    fun login(@Valid @RequestBody req: LoginRequest, http: HttpServletRequest) =
        ApiResponse.ok(auth.login(req, http.remoteAddr))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody req: RefreshRequest) = ApiResponse.ok(auth.refresh(req.refreshToken))

    @PostMapping("/logout")
    @Auditable(action = "LOGOUT")
    fun logout(@Valid @RequestBody req: RefreshRequest) = ApiResponse.ok<Any>(null, "Logged out").also { auth.logout(req.refreshToken) }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    fun change(@AuthenticationPrincipal u: UserDetails, @Valid @RequestBody r: ChangePasswordRequest) =
        ApiResponse.ok<Any>(null, "Password changed").also { userService.changePassword(u.username, r) }

    // Phase 2: Forgot password (public)
    @PostMapping("/forgot-password")
    @Auditable(action = "FORGOT_PASSWORD")
    fun forgot(@Valid @RequestBody r: ForgotPasswordRequest) =
        ApiResponse.ok<Any>(null, "If the email is registered, a reset link has been sent")
            .also { auth.forgotPassword(r.email) }

    @PostMapping("/reset-password")
    @Auditable(action = "RESET_PASSWORD")
    fun reset(@Valid @RequestBody r: ResetPasswordRequest) =
        ApiResponse.ok<Any>(null, "Password reset successful").also { auth.resetPassword(r.token, r.newPassword) }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    fun me(@AuthenticationPrincipal u: UserDetails) =
        ApiResponse.ok(userService.list(Pageable.unpaged(), u.username).items.firstOrNull())

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "UPDATE_OWN_PROFILE", entity = "User")
    fun updateMe(@AuthenticationPrincipal u: UserDetails, @Valid @RequestBody r: UpdateProfileRequest) =
        ApiResponse.ok(userService.updateOwnProfile(u.username, r))

    @PostMapping("/me/profile-image", consumes = ["multipart/form-data"])
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "UPLOAD_OWN_PROFILE_IMAGE", entity = "User")
    fun uploadMyProfileImage(@AuthenticationPrincipal u: UserDetails, @RequestParam file: MultipartFile): ApiResponse<FileMetadataDto> {
        val me = userService.list(Pageable.unpaged(), u.username).items.firstOrNull()
            ?: throw com.school.sms.exception.NotFoundException("User")
        val meta = storage.store(file, "User", me.id!!, "profile", u.username)
        userService.setProfileImage(u.username, meta.id.toString())
        return ApiResponse.ok(meta.toDto())
    }
}

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
class UserController(private val svc: UserService, private val storage: FileStorageService) {
    // Phase 1: accept both `q` and `search` to fix the search param mismatch
    @GetMapping
    fun list(pageable: Pageable,
             @RequestParam(required = false) q: String?,
             @RequestParam(required = false) search: String?) =
        ApiResponse.ok(svc.list(pageable, q ?: search))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = ApiResponse.ok(svc.get(id))

    @PostMapping
    fun create(@Valid @RequestBody r: CreateUserRequest) = ApiResponse.ok(svc.create(r))

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody r: UpdateUserRequest) = ApiResponse.ok(svc.update(id, r))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }

    @PostMapping("/{id}/profile-image", consumes = ["multipart/form-data"])
    fun uploadProfile(@PathVariable id: Long, @RequestParam file: MultipartFile, @AuthenticationPrincipal u: UserDetails): ApiResponse<FileMetadataDto> {
        val meta = storage.store(file, "User", id, "profile", u.username)
        svc.setProfileImage(svc.get(id).username, meta.id.toString())
        return ApiResponse.ok(meta.toDto())
    }
}

@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class RoleController(private val svc: RoleService) {

    @GetMapping
    fun list() = ApiResponse.ok(svc.list())

    @PostMapping
    fun create(@RequestBody r: RoleDto) = ApiResponse.ok(svc.create(r.name, r.description))

    @PostMapping("/{id}/permissions")
    fun assign(@PathVariable id: Long, @Valid @RequestBody r: AssignPermissionsRequest) =
        ApiResponse.ok(svc.assignPermissions(id, r.permissions))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }
}

@RestController
@RequestMapping("/api/teachers")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
class TeacherController(private val svc: TeacherService) {

    @GetMapping
    fun list(p: Pageable) = ApiResponse.ok(svc.list(p))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = ApiResponse.ok(svc.get(id))

    @PostMapping
    fun create(@Valid @RequestBody r: CreateTeacherRequest) = ApiResponse.ok(svc.create(r))

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody r: UpdateTeacherRequest) = ApiResponse.ok(svc.update(id, r))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }
}

@RestController
@RequestMapping("/api/students")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
class StudentController(private val svc: StudentService) {

    @GetMapping
    fun list(p: Pageable) = ApiResponse.ok(svc.list(p))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = ApiResponse.ok(svc.get(id))

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    fun create(@Valid @RequestBody r: CreateStudentRequest) = ApiResponse.ok(svc.create(r))

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpdateStudentRequest) =
        ApiResponse.ok(svc.update(id, request))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }
}

@RestController @RequestMapping("/api/parents")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
class ParentController(private val svc: ParentService) {

    @GetMapping
    fun list(p: Pageable) = ApiResponse.ok(svc.list(p))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = ApiResponse.ok(svc.get(id))

    @PostMapping
    fun create(@Valid @RequestBody r: CreateParentRequest) = ApiResponse.ok(svc.create(r))

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody r: UpdateParentRequest) = ApiResponse.ok(svc.update(id, r))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }
}

@RestController
@RequestMapping("/api/classes")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
class ClassController(private val svc: ClassService) {
    @GetMapping
    fun list() = ApiResponse.ok(svc.list())

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = ApiResponse.ok(svc.get(id))

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    fun create(@Valid @RequestBody d: SchoolClassDto) = ApiResponse.ok(svc.create(d))

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    fun update(@PathVariable id: Long, @Valid @RequestBody r: UpdateClassRequest) = ApiResponse.ok(svc.update(id, r))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }
}

@RestController
@RequestMapping("/api/subjects")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
class SubjectController(private val svc: SubjectService) {

    @GetMapping
    fun list() = ApiResponse.ok(svc.list())

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = ApiResponse.ok(svc.get(id))

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    fun create(@Valid @RequestBody d: SubjectDto) = ApiResponse.ok(svc.create(d))

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    fun update(@PathVariable id: Long, @Valid @RequestBody r: UpdateSubjectRequest) = ApiResponse.ok(svc.update(id, r))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }
}

@RestController
@RequestMapping("/api/attendance")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
class AttendanceController(private val svc: AttendanceService) {

    @GetMapping
    fun list(p: Pageable) = ApiResponse.ok(svc.list(p))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = ApiResponse.ok(svc.get(id))

    @PostMapping
    fun create(@Valid @RequestBody d: AttendanceDto) = ApiResponse.ok(svc.create(d))

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody d: AttendanceDto) = ApiResponse.ok(svc.update(id, d))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }

    @PostMapping("/mark")
    fun mark(@Valid @RequestBody r: MarkAttendanceRequest) = ApiResponse.ok(svc.mark(r))

    @GetMapping("/student/{id}")
    fun byStudent(@PathVariable id: Long, @RequestParam(required = false) from: LocalDate?, @RequestParam(required = false) to: LocalDate?) =
        ApiResponse.ok(svc.byStudent(id, from, to))

    @GetMapping("/student/{id}/percentage")
    fun pct(@PathVariable id: Long) = ApiResponse.ok(svc.percentage(id))
}

@RestController
@RequestMapping("/api/exams")
@PreAuthorize("isAuthenticated()")
class ExamController(private val svc: ExamService) {

    @GetMapping
    fun list() = ApiResponse.ok(svc.list())

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = ApiResponse.ok(svc.get(id))

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
    fun create(@Valid @RequestBody d: ExamDto) = ApiResponse.ok(svc.create(d))

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
    fun update(@PathVariable id: Long, @Valid @RequestBody r: UpdateExamRequest) = ApiResponse.ok(svc.update(id, r))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }
}

@RestController
@RequestMapping("/api/marks")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
class MarkController(private val svc: MarkService) {

    @GetMapping
    fun list() = ApiResponse.ok(svc.list())

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = ApiResponse.ok(svc.get(id))

    @PostMapping
    fun upsert(@Valid @RequestBody d: MarkDto) = ApiResponse.ok(svc.upsert(d))

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody d: MarkDto) = ApiResponse.ok(svc.upsert(d))

    @GetMapping("/student/{id}")
    fun byStudent(@PathVariable id: Long) = ApiResponse.ok(svc.byStudent(id))

    @GetMapping("/exam/{id}")
    fun byExam(@PathVariable id: Long) = ApiResponse.ok(svc.byExam(id))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }
}

@RestController
@RequestMapping("/api/fees")
@PreAuthorize("isAuthenticated()")
class FeeController(
    private val svc: FeeService,
    private val current: com.school.sms.security.CurrentUserService,
    private val studentRepo: com.school.sms.repository.StudentRepository
) {

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTANT')")
    fun list(p: Pageable) = ApiResponse.ok(svc.list(p))

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTANT')")
    fun get(@PathVariable id: Long) = ApiResponse.ok(svc.get(id))

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTANT')")
    fun create(@Valid @RequestBody d: FeeDto) = ApiResponse.ok(svc.create(d))

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTANT')")
    fun update(@PathVariable id: Long, @Valid @RequestBody r: UpdateFeeRequest) = ApiResponse.ok(svc.update(id, r))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTANT')")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }

    @GetMapping("/student/{id}")
    fun byStudent(@PathVariable id: Long): ApiResponse<List<FeeDto>> {
        // Students/Parents can only see their own children's records.
        if (!current.isAdmin() && !current.hasAnyRole("ACCOUNTANT")) {
            if (current.isStudent()) {
                if (current.studentOrThrow().id != id)
                    throw com.school.sms.exception.ForbiddenException("Cannot view other students' fees")
            } else if (current.isParent()) {
                val parentId = current.parentOrThrow().id
                val ok = studentRepo.findById(id).map { it.parent?.id == parentId }.orElse(false)
                if (!ok) throw com.school.sms.exception.ForbiddenException("Not your child")
            } else throw com.school.sms.exception.ForbiddenException("Access denied")
        }
        return ApiResponse.ok(svc.byStudent(id))
    }

    @PostMapping("/pay")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTANT')")
    fun pay(@Valid @RequestBody r: PayFeeRequest) = ApiResponse.ok(svc.pay(r))

    @PostMapping("/reminders")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACCOUNTANT')")
    fun remind() = ApiResponse.ok<Any>(null, "Reminders sent").also { svc.sendReminders() }
}

@RestController
@RequestMapping("/api/assignments")
@PreAuthorize("isAuthenticated()")
class AssignmentController(
    private val svc: AssignmentService,
    private val storage: FileStorageService,
    private val current: com.school.sms.security.CurrentUserService
) {

    @GetMapping
    fun list() = ApiResponse.ok(svc.list())

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = ApiResponse.ok(svc.get(id))

    /** JSON-only create for the generic CRUD form (no file upload). */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
    fun createJson(@Valid @RequestBody d: AssignmentDto) = ApiResponse.ok(svc.create(d, null))

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
    fun update(@PathVariable id: Long, @Valid @RequestBody r: UpdateAssignmentRequest) = ApiResponse.ok(svc.update(id, r))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }

    @PostMapping("/upload", consumes = ["multipart/form-data"])
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
    fun createWithFile(
        @RequestPart("data") d: AssignmentDto,
        @RequestPart(name = "file", required = false) file: MultipartFile?,
        @AuthenticationPrincipal u: UserDetails
    ): ApiResponse<AssignmentDto> {
        val path = file?.let { storage.store(it, "Assignment", 0, "assignment", u.username).storedPath }
        return ApiResponse.ok(svc.create(d, path))
    }

    @PostMapping("/{id}/submit", consumes = ["multipart/form-data"])
    @PreAuthorize("hasRole('STUDENT')")
    fun submit(@PathVariable id: Long,
        @RequestPart file: MultipartFile, @AuthenticationPrincipal u: UserDetails): ApiResponse<SubmissionDto> {
        // Derive studentId from JWT — never trust frontend.
        val studentId = current.studentOrThrow().id!!
        val meta = storage.store(file, "Submission", id, "submission", u.username)
        return ApiResponse.ok(svc.submit(id, studentId, meta.storedPath))
    }

    @PostMapping("/submissions/{id}/grade")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
    fun grade(@PathVariable id: Long, @RequestBody r: GradeSubmissionRequest) = ApiResponse.ok(svc.grade(id, r))

    @GetMapping("/{id}/submissions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER')")
    fun submissions(@PathVariable id: Long) = ApiResponse.ok(svc.submissions(id))
}

@RestController @RequestMapping("/api/library")
@PreAuthorize("isAuthenticated()")
class LibraryController(private val svc: LibraryService) {

    @GetMapping("/books")
    fun books() = ApiResponse.ok(svc.list())

    @PostMapping("/books")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','LIBRARIAN')")
    fun create(@RequestBody d: BookDto) = ApiResponse.ok(svc.create(d))

    @PostMapping("/issue")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','LIBRARIAN')")
    fun issue(@Valid @RequestBody r: IssueBookRequest) = ApiResponse.ok(svc.issue(r))

    @PostMapping("/return/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','LIBRARIAN')")
    fun ret(@PathVariable id: Long) = ApiResponse.ok(svc.returnBook(id))
}

@RestController
@RequestMapping("/api/notices")
@PreAuthorize("isAuthenticated()")
class NoticeController(private val svc: NoticeService) {

    @GetMapping
    fun list() = ApiResponse.ok(svc.list())

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long) = ApiResponse.ok(svc.get(id))

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    fun create(@Valid @RequestBody d: NoticeDto) = ApiResponse.ok(svc.create(d))

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    fun update(@PathVariable id: Long, @Valid @RequestBody r: UpdateNoticeRequest) = ApiResponse.ok(svc.update(id, r))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    fun delete(@PathVariable id: Long) = ApiResponse.ok<Any>(null, "Deleted").also { svc.delete(id) }
}

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("isAuthenticated()")
class DashboardController(
    private val users: com.school.sms.repository.UserRepository,
    private val teachers: com.school.sms.repository.TeacherRepository,
    private val students: com.school.sms.repository.StudentRepository,
    private val classes: com.school.sms.repository.SchoolClassRepository,
    private val subjects: com.school.sms.repository.SubjectRepository,
    private val fees: com.school.sms.repository.FeeRepository,
    private val books: com.school.sms.repository.BookRepository,
    private val issues: com.school.sms.repository.BookIssueRepository,
    private val assignments: com.school.sms.repository.AssignmentRepository,
    private val subs: com.school.sms.repository.AssignmentSubmissionRepository,
    private val marks: com.school.sms.repository.MarkRepository,
    private val notices: com.school.sms.repository.NoticeRepository,
    private val exams: com.school.sms.repository.ExamRepository,
    private val attendance: AttendanceService,
    private val current: com.school.sms.security.CurrentUserService
) {
    @GetMapping
    fun dashboard(): ApiResponse<Map<String, Any?>> {
        val roles = current.roles().toList()
        val data = mutableMapOf<String, Any?>("roles" to roles)
        when {
            current.isAdmin() -> {
                data["totalUsers"] = users.count()
                data["totalTeachers"] = teachers.count()
                data["totalStudents"] = students.count()
                data["totalClasses"] = classes.count()
                val all = fees.findAll()
                data["totalRevenue"] = all.sumOf { it.amountPaid }
                data["pendingFees"] = all.filter { it.status != com.school.sms.entity.FeeStatus.PAID }.sumOf { it.amount - it.amountPaid }
                data["totalBooks"] = books.count()
                data["booksIssued"] = issues.findAll().count { it.returnedOn == null }
                data["recentNotices"] = notices.findAll().sortedByDescending { it.id }.take(5).map { mapOf("id" to it.id, "title" to it.title, "publishedOn" to it.publishedOn) }
            }
            current.isTeacher() -> runCatching { current.teacherOrThrow() }.getOrNull()?.let { t ->
                val mySubs = subjects.findByTeacherId(t.id!!)
                val classIds = mySubs.mapNotNull { it.schoolClass?.id }.toSet()
                val myStudents = classIds.flatMap { students.findBySchoolClassId(it) }.distinctBy { it.id }
                val myAssignments = assignments.findByTeacherId(t.id!!)
                val pending = myAssignments.mapNotNull { it.id }
                    .sumOf { aid -> subs.findByAssignmentId(aid).count { s -> s.grade.isNullOrBlank() } }
                data["myTeacherId"] = t.id
                data["myClasses"] = classIds.size
                data["myStudents"] = myStudents.size
                data["pendingSubmissions"] = pending
                data["pendingAssignments"] = myAssignments.count { it.dueDate?.isAfter(java.time.LocalDate.now()) ?: true }
                data["upcomingExams"] = exams.findAll().count { (it.startDate ?: java.time.LocalDate.MIN) >= java.time.LocalDate.now() }
            }
            current.isStudent() -> runCatching { current.studentOrThrow() }.getOrNull()?.let { s ->
                val sid = s.id!!
                val pct = attendance.percentage(sid)
                val myMarks = marks.findByStudentId(sid)
                val avg = if (myMarks.isEmpty()) 0.0 else myMarks.map { it.marksObtained.toDouble() / (it.maxMarks.toDouble().takeIf { v -> v > 0 } ?: 1.0) * 100 }.average()
                val myFees = fees.findByStudentId(sid)
                val pending = myFees.filter { it.status != com.school.sms.entity.FeeStatus.PAID }.sumOf { it.amount - it.amountPaid }
                val mySubs = subs.findByStudentId(sid).map { it.assignment?.id }.toSet()
                val pendingAssignments = assignments.findAll().count { it.id !in mySubs }
                data["myStudentId"] = sid
                data["attendancePercent"] = pct
                data["averageMarks"] = String.format("%.1f", avg)
                data["pendingFees"] = pending
                data["pendingAssignments"] = pendingAssignments
                data["upcomingExams"] = exams.findAll().filter { (it.startDate ?: java.time.LocalDate.MIN) >= java.time.LocalDate.now() }
                    .take(5).map { mapOf("id" to it.id, "name" to it.name, "startDate" to it.startDate) }
                data["libraryIssued"] = issues.findByStudentIdAndReturnedOnIsNull(sid).size
                data["recentNotices"] = notices.findAll().sortedByDescending { it.id }.take(5).map { mapOf("id" to it.id, "title" to it.title, "publishedOn" to it.publishedOn) }
            }
            current.isParent() -> runCatching { current.parentOrThrow() }.getOrNull()?.let { p ->
                val children = students.findByParentId(p.id!!)
                val childIds = children.mapNotNull { it.id }
                val totalChildren = children.size

                val attendancePercents = childIds.map { attendance.percentage(it) }
                val avgAttendance = if (attendancePercents.isEmpty()) 0.0
                    else attendancePercents.average()

                val allFees = childIds.flatMap { fees.findByStudentId(it) }
                val pendingFees = allFees
                    .filter { it.status != com.school.sms.entity.FeeStatus.PAID }
                    .sumOf { it.amount - it.amountPaid }

                val allMarks = childIds.flatMap { marks.findByStudentId(it) }
                val avgMarks = if (allMarks.isEmpty()) 0.0
                    else allMarks.map {
                        it.marksObtained.toDouble() / (it.maxMarks.toDouble().takeIf { v -> v > 0 } ?: 1.0) * 100
                    }.average()

                val submittedByChild = childIds.associateWith { sid ->
                    subs.findByStudentId(sid).mapNotNull { it.assignment?.id }.toSet()
                }
                val allAssignments = assignments.findAll()
                val pendingAssignments = childIds.sumOf { sid ->
                    val sub = submittedByChild[sid].orEmpty()
                    allAssignments.count { it.id !in sub }
                }

                val upcoming = exams.findAll()
                    .filter { (it.startDate ?: java.time.LocalDate.MIN) >= java.time.LocalDate.now() }
                    .sortedBy { it.startDate }
                    .take(5)
                    .map { mapOf("id" to it.id, "name" to it.name, "startDate" to it.startDate) }

                val recentNotices = notices.findAll().sortedByDescending { it.id }.take(5)
                    .map { mapOf("id" to it.id, "title" to it.title, "publishedOn" to it.publishedOn) }

                val childSummaries = children.map { s ->
                    val sid = s.id!!
                    val myFees = fees.findByStudentId(sid)
                    mapOf(
                        "studentId" to sid,
                        "name" to (s.user?.fullName ?: ""),
                        "admissionNo" to s.admissionNo,
                        "className" to s.schoolClass?.name,
                        "section" to s.schoolClass?.section,
                        "attendancePercent" to attendance.percentage(sid),
                        "pendingFees" to myFees.filter { it.status != com.school.sms.entity.FeeStatus.PAID }
                            .sumOf { it.amount - it.amountPaid },
                        "pendingAssignments" to allAssignments.count { it.id !in submittedByChild[sid].orEmpty() }
                    )
                }

                data["children"] = totalChildren
                data["totalChildren"] = totalChildren
                data["avgAttendance"] = String.format("%.1f", avgAttendance).toDouble()
                data["averageMarks"] = String.format("%.1f", avgMarks)
                data["pendingFees"] = pendingFees
                data["pendingAssignments"] = pendingAssignments
                data["unreadNotices"] = recentNotices.size
                data["upcomingExams"] = upcoming
                data["recentNotices"] = recentNotices
                data["childSummaries"] = childSummaries
            }
        }
        return ApiResponse.ok(data)
    }
}

@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class AuditLogController(private val repo: com.school.sms.repository.AuditLogRepository) {

    @GetMapping
    fun list(p: Pageable) = ApiResponse.ok(repo.findAll(p).map {
        AuditLogDto(it.id, it.username, it.action, it.entity, it.entityId, it.ip, it.success, it.createdAt)
    })
}
