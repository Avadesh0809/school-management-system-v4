package com.school.sms.service

import com.school.sms.dto.*
import com.school.sms.entity.*
import com.school.sms.exception.*
import com.school.sms.rbac.Roles
import com.school.sms.repository.*
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

/** Wraps a delete call, converting FK-violations into a friendly Conflict. */
private inline fun safeDelete(label: String, block: () -> Unit) {
    try { block() } catch (e: DataIntegrityViolationException) {
        throw ConflictException("Cannot delete $label: it is referenced by other records")
    }
}

private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.')

@Service
@Transactional
class RoleService(private val roles: RoleRepository, private val perms: PermissionRepository, private val rp: RolePermissionRepository) {

    fun toDto(r: Role): RoleDto {
        val permNames = rp.findByRoleId(r.id!!).mapNotNull { it.permission?.name }
        return RoleDto(r.id, r.name, r.description, permNames)
    }

    fun list() = roles.findAll().map(::toDto)
    fun create(name: String, desc: String?) = toDto(roles.save(Role(name, desc)))
    fun delete(id: Long) = safeDelete("Role") { roles.deleteById(id) }

    fun assignPermissions(roleId: Long, permNames: Set<String>): RoleDto {
        val role = roles.findById(roleId).orElseThrow { NotFoundException("Role") }
        rp.findByRoleId(roleId).forEach { rp.delete(it) }
        permNames.forEach { pn ->
            val p = perms.findByName(pn).orElseThrow { BadRequestException("Permission $pn") }
            rp.save(RolePermission(role, p))
        }
        return toDto(role)
    }
}

/** Helper to auto-create a User account for a Student/Teacher/Parent. */
@Service
class AutoUserFactory(
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val encoder: PasswordEncoder
) {
    fun resolveOrCreate(
        userId: Long?, fullName: String?, email: String?, phone: String?, password: String?,
        defaultRole: String, suggestedUsername: String
    ): User {
        if (userId != null) {
            return users.findById(userId).orElseThrow { BadRequestException("User $userId not found") }
        }
        val name = fullName?.trim().orEmpty().ifBlank { throw BadRequestException("fullName is required") }
        val mail = email?.trim().orEmpty().ifBlank { throw BadRequestException("email is required") }
        if (users.existsByEmail(mail)) throw ConflictException("A user with email $mail already exists")

        var username = slug(suggestedUsername.ifBlank { mail.substringBefore('@') })
        if (username.length < 3) username = "${username}.user"
        var unique = username; var i = 1
        while (users.existsByUsername(unique)) { unique = "$username$i"; i++ }

        val pwd = (password?.takeIf { it.length >= 6 }) ?: ("Pass@" + (1000..9999).random())
        val role = roles.findByName(defaultRole).orElseThrow { BadRequestException("Role $defaultRole missing") }
        val u = User(unique, mail, encoder.encode(pwd), name, phone)
        u.roles = mutableSetOf(role)
        return users.save(u)
    }

    fun updateLinkedUser(u: User?, fullName: String?, email: String?, phone: String?) {
        if (u == null) return
        fullName?.let { u.fullName = it }
        email?.let { u.email = it }
        phone?.let { u.phone = it }
        users.save(u)
    }
}

@Service
@Transactional
class TeacherService(
    private val repo: TeacherRepository,
    private val userFactory: AutoUserFactory
) {
    fun toDto(t: Teacher) = TeacherDto(
        t.id, t.user?.id, t.user?.fullName, t.user?.email, t.user?.phone,
        t.employeeCode, t.qualification, t.specialization, t.dateOfJoining, t.salary
    )

    fun list(p: Pageable) = repo.findAll(p).let { PagedResponse(it.content.map(::toDto), it.number, it.size, it.totalElements, it.totalPages) }
    fun get(id: Long) = repo.findById(id).orElseThrow { NotFoundException("Teacher") }.let(::toDto)

    fun create(r: CreateTeacherRequest): TeacherDto {
        val user = userFactory.resolveOrCreate(
            r.userId, r.fullName, r.email, r.phone, r.password,
            Roles.TEACHER, r.employeeCode.ifBlank { r.fullName ?: "" }
        )
        return toDto(repo.save(Teacher(user, r.employeeCode, r.qualification, r.specialization, r.dateOfJoining, r.salary)))
    }

    fun update(id: Long, r: UpdateTeacherRequest): TeacherDto {
        val t = repo.findById(id).orElseThrow { NotFoundException("Teacher not found with id: $id") }
        userFactory.updateLinkedUser(t.user, r.fullName, r.email, r.phone)
        r.employeeCode?.let { t.employeeCode = it }
        r.qualification?.let { t.qualification = it }
        r.specialization?.let { t.specialization = it }
        r.dateOfJoining?.let { t.dateOfJoining = it }
        r.salary?.let { t.salary = it }
        return toDto(repo.save(t))
    }

    fun delete(id: Long) = safeDelete("Teacher") { repo.deleteById(id) }
}

@Service
@Transactional
class StudentService(
    private val repo: StudentRepository,
    private val classes: SchoolClassRepository,
    private val parents: ParentRepository,
    private val userFactory: AutoUserFactory
) {
    fun toDto(s: Student) = StudentDto(
        s.id, s.user?.id, s.user?.fullName, s.user?.email, s.user?.phone,
        s.admissionNo, s.schoolClass?.id, s.schoolClass?.name,
        s.parent?.id, s.parent?.user?.fullName,
        s.dateOfBirth, s.gender, s.address
    )

    fun list(p: Pageable) = repo.findAll(p).let { PagedResponse(it.content.map(::toDto), it.number, it.size, it.totalElements, it.totalPages) }
    fun get(id: Long) = repo.findById(id).orElseThrow { NotFoundException("Student") }.let(::toDto)

    fun create(r: CreateStudentRequest): StudentDto {
        val user = userFactory.resolveOrCreate(
            r.userId, r.fullName, r.email, r.phone, r.password,
            Roles.STUDENT, r.admissionNo.ifBlank { r.fullName ?: "" }
        )
        val s = Student(user, r.admissionNo)
        r.classId?.let { s.schoolClass = classes.findById(it).orElseThrow { BadRequestException("Class $it not found") } }
        r.parentId?.let { s.parent = parents.findById(it).orElseThrow { BadRequestException("Parent $it not found") } }
        s.dateOfBirth = r.dateOfBirth; s.gender = r.gender; s.address = r.address
        return toDto(repo.save(s))
    }

    fun update(id: Long, r: UpdateStudentRequest): StudentDto {
        val student = repo.findById(id).orElseThrow { NotFoundException("Student not found with id: $id") }
        userFactory.updateLinkedUser(student.user, r.fullName, r.email, r.phone)
        r.admissionNo?.let { student.admissionNo = it }
        r.dateOfBirth?.let { student.dateOfBirth = it }
        r.gender?.let { student.gender = it }
        r.address?.let { student.address = it }
        r.classId?.let { student.schoolClass = classes.findById(it).orElseThrow { NotFoundException("Class $it") } }
        r.parentId?.let { student.parent = parents.findById(it).orElseThrow { NotFoundException("Parent $it") } }
        return toDto(repo.save(student))
    }

    fun delete(id: Long) = safeDelete("Student") { repo.deleteById(id) }
}

@Service
@Transactional
class ParentService(private val repo: ParentRepository, private val userFactory: AutoUserFactory) {

    fun toDto(p: Parent) = ParentDto(p.id, p.user?.id, p.user?.fullName, p.user?.email, p.user?.phone, p.occupation, p.address)

    fun list(pg: Pageable) = repo.findAll(pg).let { PagedResponse(it.content.map(::toDto), it.number, it.size, it.totalElements, it.totalPages) }
    fun get(id: Long) = repo.findById(id).orElseThrow { NotFoundException("Parent") }.let(::toDto)

    fun create(r: CreateParentRequest): ParentDto {
        val u = userFactory.resolveOrCreate(
            r.userId, r.fullName, r.email, r.phone, r.password,
            Roles.PARENT, r.fullName ?: r.email ?: ""
        )
        return toDto(repo.save(Parent(u, r.occupation, r.address)))
    }

    fun update(id: Long, r: UpdateParentRequest): ParentDto {
        val p = repo.findById(id).orElseThrow { NotFoundException("Parent not found with id: $id") }
        userFactory.updateLinkedUser(p.user, r.fullName, r.email, r.phone)
        r.occupation?.let { p.occupation = it }
        r.address?.let { p.address = it }
        return toDto(repo.save(p))
    }

    fun delete(id: Long) = safeDelete("Parent") { repo.deleteById(id) }
}

@Service
@Transactional
class ClassService(private val repo: SchoolClassRepository, private val teachers: TeacherRepository) {

    fun toDto(c: SchoolClass) = SchoolClassDto(
        c.id, c.name, c.section, c.academicYear, c.classTeacher?.id, c.classTeacher?.user?.fullName
    )

    fun list() = repo.findAll().map(::toDto)
    fun get(id: Long) = repo.findById(id).orElseThrow { NotFoundException("Class") }.let(::toDto)

    fun create(d: SchoolClassDto): SchoolClassDto {
        val c = SchoolClass(d.name, d.section, d.academicYear)
        d.classTeacherId?.let { c.classTeacher = teachers.findById(it).orElseThrow { BadRequestException("Teacher $it not found") } }
        return toDto(repo.save(c))
    }

    fun update(id: Long, r: UpdateClassRequest): SchoolClassDto {
        val c = repo.findById(id).orElseThrow { NotFoundException("Class not found with id: $id") }
        r.name?.let { c.name = it }
        r.section?.let { c.section = it }
        r.academicYear?.let { c.academicYear = it }
        r.classTeacherId?.let { c.classTeacher = teachers.findById(it).orElseThrow { NotFoundException("Teacher $it") } }
        return toDto(repo.save(c))
    }

    fun delete(id: Long) = safeDelete("Class") { repo.deleteById(id) }
}

@Service
@Transactional
class SubjectService(private val repo: SubjectRepository, private val classes: SchoolClassRepository, private val teachers: TeacherRepository) {

    fun toDto(s: Subject) = SubjectDto(
        s.id, s.code, s.name,
        s.schoolClass?.id, s.schoolClass?.name,
        s.teacher?.id, s.teacher?.user?.fullName
    )

    fun list() = repo.findAll().map(::toDto)
    fun get(id: Long) = repo.findById(id).orElseThrow { NotFoundException("Subject") }.let(::toDto)

    fun create(d: SubjectDto): SubjectDto {
        val s = Subject(d.code, d.name)
        d.classId?.let { s.schoolClass = classes.findById(it).orElseThrow { BadRequestException("Class $it not found") } }
        d.teacherId?.let { s.teacher = teachers.findById(it).orElseThrow { BadRequestException("Teacher $it not found") } }
        return toDto(repo.save(s))
    }

    fun update(id: Long, r: UpdateSubjectRequest): SubjectDto {
        val s = repo.findById(id).orElseThrow { NotFoundException("Subject not found with id: $id") }
        r.code?.let { s.code = it }
        r.name?.let { s.name = it }
        r.classId?.let { s.schoolClass = classes.findById(it).orElseThrow { NotFoundException("Class $it") } }
        r.teacherId?.let { s.teacher = teachers.findById(it).orElseThrow { NotFoundException("Teacher $it") } }
        return toDto(repo.save(s))
    }

    fun delete(id: Long) = safeDelete("Subject") { repo.deleteById(id) }
}

@Service
@Transactional
class AttendanceService(private val repo: AttendanceRepository, private val students: StudentRepository) {

    fun toDto(a: Attendance) = AttendanceDto(
        a.id, a.student!!.id!!, a.student?.user?.fullName, a.date, a.status.name, a.remarks
    )

    fun list(p: Pageable) = repo.findAll(p).let { PagedResponse(it.content.map(::toDto), it.number, it.size, it.totalElements, it.totalPages) }
    fun get(id: Long) = repo.findById(id).orElseThrow { NotFoundException("Attendance") }.let(::toDto)
    fun delete(id: Long) = safeDelete("Attendance") { repo.deleteById(id) }

    private fun parseStatus(s: String) =
        try { AttendanceStatus.valueOf(s.uppercase()) }
        catch (_: IllegalArgumentException) { throw BadRequestException("Invalid attendance status: $s") }

    fun create(d: AttendanceDto): AttendanceDto = saveOne(d)

    fun update(id: Long, d: AttendanceDto): AttendanceDto {
        val a = repo.findById(id).orElseThrow { NotFoundException("Attendance $id") }
        a.status = parseStatus(d.status); a.remarks = d.remarks; a.date = d.date
        return toDto(repo.save(a))
    }

    /** Upsert by (studentId, date) — protects against duplicates. */
    fun mark(req: MarkAttendanceRequest): List<AttendanceDto> = req.records.map(::saveOne)

    private fun saveOne(rec: AttendanceDto): AttendanceDto {
        val s = students.findById(rec.studentId).orElseThrow { BadRequestException("Student ${rec.studentId}") }
        val status = parseStatus(rec.status)
        val existing = repo.findByStudentIdAndDate(rec.studentId, rec.date).orElse(null)
        val a = if (existing != null) {
            existing.status = status; existing.remarks = rec.remarks; existing
        } else Attendance(s, rec.date, status, rec.remarks)
        return try { toDto(repo.save(a)) }
        catch (e: DataIntegrityViolationException) {
            throw ConflictException("Attendance already recorded for student ${rec.studentId} on ${rec.date}")
        }
    }

    fun byStudent(id: Long, from: LocalDate?, to: LocalDate?) =
        if (from != null && to != null)
            repo.findByStudentIdAndDateBetween(id, from, to).map(::toDto)
        else
            repo.findByStudentId(id).map(::toDto)

    fun percentage(studentId: Long): Double {
        val total = repo.countTotal(studentId); val present = repo.countPresent(studentId)
        return if (total == 0L) 0.0 else (present.toDouble() / total) * 100
    }
}

@Service
@Transactional
class ExamService(private val repo: ExamRepository) {

    fun toDto(e: Exam) = ExamDto(e.id, e.name, e.academicYear, e.startDate, e.endDate)

    fun list() = repo.findAll().map(::toDto)
    fun get(id: Long) = repo.findById(id).orElseThrow { NotFoundException("Exam") }.let(::toDto)
    fun create(d: ExamDto) = toDto(repo.save(Exam(d.name, d.academicYear, d.startDate, d.endDate)))

    fun update(id: Long, r: UpdateExamRequest): ExamDto {
        val e = repo.findById(id).orElseThrow { NotFoundException("Exam not found with id: $id") }
        r.name?.let { e.name = it }
        r.academicYear?.let { e.academicYear = it }
        r.startDate?.let { e.startDate = it }
        r.endDate?.let { e.endDate = it }
        return toDto(repo.save(e))
    }

    fun delete(id: Long) = safeDelete("Exam") { repo.deleteById(id) }
}

@Service
@Transactional
class MarkService(
    private val repo: MarkRepository,
    private val exams: ExamRepository,
    private val students: StudentRepository,
    private val subjects: SubjectRepository
) {
    fun toDto(m: Mark) = MarkDto(
        m.id,
        m.exam!!.id!!, m.exam?.name,
        m.student!!.id!!, m.student?.user?.fullName,
        m.subject!!.id!!, m.subject?.name,
        m.marksObtained, m.maxMarks, m.grade
    )

    fun list() = repo.findAll().map(::toDto)
    fun get(id: Long) = repo.findById(id).orElseThrow { NotFoundException("Mark") }.let(::toDto)
    fun delete(id: Long) = safeDelete("Mark") { repo.deleteById(id) }

    /** Real upsert by (examId, studentId, subjectId). */
    fun upsert(d: MarkDto): MarkDto {
        val exam = exams.findById(d.examId).orElseThrow { BadRequestException("Exam") }
        val student = students.findById(d.studentId).orElseThrow { BadRequestException("Student") }
        val subject = subjects.findById(d.subjectId).orElseThrow { BadRequestException("Subject") }
        val finalGrade = d.grade ?: grade(d.marksObtained, d.maxMarks)

        val existing = repo.findByExamIdAndStudentIdAndSubjectId(d.examId, d.studentId, d.subjectId).orElse(null)
        val m = if (existing != null) {
            existing.marksObtained = d.marksObtained
            existing.maxMarks = d.maxMarks
            existing.grade = finalGrade
            existing
        } else Mark(exam, student, subject, d.marksObtained, d.maxMarks, finalGrade)
        return toDto(repo.save(m))
    }

    fun byStudent(id: Long) = repo.findByStudentId(id).map(::toDto)
    fun byExam(id: Long) = repo.findByExamId(id).map(::toDto)

    private fun grade(obtained: BigDecimal, max: BigDecimal): String {
        if (max.signum() == 0) return "F"
        val pct = obtained.toDouble() / max.toDouble() * 100
        return when { pct >= 90 -> "A+"; pct >= 80 -> "A"; pct >= 70 -> "B"; pct >= 60 -> "C"; pct >= 50 -> "D"; else -> "F" }
    }
}

@Service
@Transactional
class FeeService(
    private val repo: FeeRepository, private val payments: FeePaymentRepository,
    private val students: StudentRepository, private val email: com.school.sms.email.EmailService
) {
    fun toDto(f: Fee) = FeeDto(
        f.id, f.student!!.id!!, f.student?.user?.fullName,
        f.term, f.amount, f.amountPaid, f.dueDate, f.status.name
    )

    fun list(p: Pageable) = repo.findAll(p).let { PagedResponse(it.content.map(::toDto), it.number, it.size, it.totalElements, it.totalPages) }
    fun get(id: Long) = repo.findById(id).orElseThrow { NotFoundException("Fee") }.let(::toDto)
    fun byStudent(id: Long) = repo.findByStudentId(id).map(::toDto)

    fun create(d: FeeDto): FeeDto {
        val s = students.findById(d.studentId).orElseThrow { BadRequestException("Student") }
        val f = Fee(s, d.term, d.amount, BigDecimal.ZERO, d.dueDate, FeeStatus.PENDING)
        return toDto(repo.save(f))
    }

    fun update(id: Long, r: UpdateFeeRequest): FeeDto {
        val f = repo.findById(id).orElseThrow { NotFoundException("Fee not found with id: $id") }
        r.term?.let { f.term = it }
        r.amount?.let { f.amount = it }
        r.dueDate?.let { f.dueDate = it }
        r.status?.let {
            try { f.status = FeeStatus.valueOf(it.uppercase()) }
            catch (_: IllegalArgumentException) { throw BadRequestException("Invalid fee status: $it") }
        }
        if (r.status == null && r.amount != null) {
            f.status = when {
                f.amountPaid >= f.amount -> FeeStatus.PAID
                f.amountPaid > BigDecimal.ZERO -> FeeStatus.PARTIAL
                else -> FeeStatus.PENDING
            }
        }
        return toDto(repo.save(f))
    }

    fun delete(id: Long) = safeDelete("Fee") { repo.deleteById(id) }

    fun pay(r: PayFeeRequest): FeeDto {
        val f = repo.findById(r.feeId!!).orElseThrow { NotFoundException("Fee") }
        payments.save(FeePayment(f, r.amount, LocalDate.now(), r.method, r.reference))
        f.amountPaid = f.amountPaid + r.amount
        f.status = when { f.amountPaid >= f.amount -> FeeStatus.PAID; f.amountPaid > BigDecimal.ZERO -> FeeStatus.PARTIAL; else -> FeeStatus.PENDING }
        return toDto(repo.save(f))
    }

    fun sendReminders() {
        repo.findByStatus(FeeStatus.PENDING).forEach { f ->
            val u = f.student?.user ?: return@forEach
            email.sendFeeReminder(u.email, u.fullName, f.amount.toPlainString(), f.dueDate?.toString() ?: "-")
        }
    }
}

@Service
@Transactional
class AssignmentService(
    private val repo: AssignmentRepository, private val subs: AssignmentSubmissionRepository,
    private val subjects: SubjectRepository, private val teachers: TeacherRepository,
    private val students: StudentRepository, private val email: com.school.sms.email.EmailService
) {
    fun toDto(a: Assignment) = AssignmentDto(
        a.id, a.title, a.description,
        a.subject?.id, a.subject?.name,
        a.teacher?.id, a.teacher?.user?.fullName,
        a.dueDate, a.filePath
    )
    fun subDto(s: AssignmentSubmission) = SubmissionDto(s.id, s.assignment!!.id!!, s.student!!.id!!, s.filePath, s.submittedAt, s.grade, s.feedback)

    fun list() = repo.findAll().map(::toDto)
    fun get(id: Long) = repo.findById(id).orElseThrow { NotFoundException("Assignment") }.let(::toDto)

    fun create(d: AssignmentDto, filePath: String? = null): AssignmentDto {
        val a = Assignment(d.title, d.description)
        d.subjectId?.let { a.subject = subjects.findById(it).orElseThrow { BadRequestException("Subject $it not found") } }
        d.teacherId?.let { a.teacher = teachers.findById(it).orElseThrow { BadRequestException("Teacher $it not found") } }
        a.dueDate = d.dueDate; a.filePath = filePath ?: d.filePath
        val saved = repo.save(a)
        a.subject?.schoolClass?.id?.let { cid ->
            students.findBySchoolClassId(cid).forEach { s ->
                s.user?.email?.let { email.sendAssignmentNotification(it, saved.title, saved.dueDate?.toString() ?: "-") }
            }
        }
        return toDto(saved)
    }

    fun update(id: Long, r: UpdateAssignmentRequest): AssignmentDto {
        val a = repo.findById(id).orElseThrow { NotFoundException("Assignment $id") }
        r.title?.let { a.title = it }
        r.description?.let { a.description = it }
        r.dueDate?.let { a.dueDate = it }
        r.subjectId?.let { a.subject = subjects.findById(it).orElseThrow { NotFoundException("Subject $it") } }
        r.teacherId?.let { a.teacher = teachers.findById(it).orElseThrow { NotFoundException("Teacher $it") } }
        return toDto(repo.save(a))
    }

    fun delete(id: Long) = safeDelete("Assignment") { repo.deleteById(id) }

    fun submit(assignmentId: Long, studentId: Long, filePath: String?): SubmissionDto {
        val a = repo.findById(assignmentId).orElseThrow { NotFoundException("Assignment") }
        val s = students.findById(studentId).orElseThrow { NotFoundException("Student") }
        val existing = subs.findByAssignmentIdAndStudentId(assignmentId, studentId).orElse(null)
        val sub = existing ?: AssignmentSubmission(a, s)
        sub.filePath = filePath; sub.submittedAt = java.time.LocalDateTime.now()
        return subDto(subs.save(sub))
    }

    fun grade(id: Long, r: GradeSubmissionRequest): SubmissionDto {
        val s = subs.findById(id).orElseThrow { NotFoundException("Submission") }
        s.grade = r.grade; s.feedback = r.feedback
        return subDto(subs.save(s))
    }

    fun submissions(assignmentId: Long) = subs.findByAssignmentId(assignmentId).map(::subDto)
}

@Service
@Transactional
class LibraryService(private val books: BookRepository, private val issues: BookIssueRepository, private val students: StudentRepository) {

    fun toDto(b: Book) = BookDto(b.id, b.isbn, b.title, b.author, b.totalCopies, b.availableCopies)

    fun list() = books.findAll().map(::toDto)
    fun create(d: BookDto) = toDto(books.save(Book(d.isbn, d.title, d.author, d.totalCopies, d.availableCopies.takeIf { it > 0 } ?: d.totalCopies)))

    fun issue(r: IssueBookRequest): BookIssue {
        val b = books.findById(r.bookId!!).orElseThrow { NotFoundException("Book") }
        if (b.availableCopies <= 0) throw BadRequestException("Not available")
        val s = students.findById(r.studentId!!).orElseThrow { NotFoundException("Student") }
        b.availableCopies -= 1; books.save(b)
        return issues.save(BookIssue(b, s))
    }

    fun returnBook(issueId: Long): BookIssue {
        val iss = issues.findById(issueId).orElseThrow { NotFoundException("Issue") }
        if (iss.returnedOn != null) throw BadRequestException("Already returned")
        iss.returnedOn = LocalDate.now()
        iss.book?.let { it.availableCopies += 1; books.save(it) }
        return issues.save(iss)
    }
}

@Service
@Transactional
class NoticeService(private val repo: NoticeRepository) {

    fun toDto(n: Notice) = NoticeDto(n.id, n.title, n.content, n.targetRole, n.publishedOn)

    fun list() = repo.findAll().map(::toDto)
    fun get(id: Long) = repo.findById(id).orElseThrow { NotFoundException("Notice") }.let(::toDto)
    fun create(d: NoticeDto) = toDto(repo.save(Notice(d.title, d.content, d.targetRole, d.publishedOn ?: LocalDate.now())))

    fun update(id: Long, r: UpdateNoticeRequest): NoticeDto {
        val n = repo.findById(id).orElseThrow { NotFoundException("Notice $id") }
        r.title?.let { n.title = it }
        r.content?.let { n.content = it }
        r.targetRole?.let { n.targetRole = it }
        r.publishedOn?.let { n.publishedOn = it }
        return toDto(repo.save(n))
    }

    fun delete(id: Long) = safeDelete("Notice") { repo.deleteById(id) }
}
