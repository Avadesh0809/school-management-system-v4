package com.school.sms.controller

import com.school.sms.dto.ApiResponse
import com.school.sms.dto.BookDto
import com.school.sms.repository.*
import com.school.sms.security.CurrentUserService
import com.school.sms.service.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * Secure self-service endpoints for the authenticated user.
 * IDs are derived from the JWT/session — never from the request body or path.
 */
@RestController
@RequestMapping("/api/student/me")
@PreAuthorize("hasRole('STUDENT')")
class StudentSelfController(
    private val current: CurrentUserService,
    private val studentSvc: StudentService,
    private val attendance: AttendanceService,
    private val marks: MarkService,
    private val fees: FeeService,
    private val assignments: AssignmentService,
    private val assignmentSubs: AssignmentSubmissionRepository,
    private val exams: ExamService,
    private val notices: NoticeService,
    private val issues: BookIssueRepository,
    private val books: BookRepository,
    private val subjects: SubjectRepository
) {
    @GetMapping
    fun me() = ApiResponse.ok(studentSvc.get(current.studentOrThrow().id!!))

    @GetMapping("/attendance")
    fun myAttendance(
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?
    ) = ApiResponse.ok(attendance.byStudent(current.studentOrThrow().id!!, from, to))

    @GetMapping("/attendance/percentage")
    fun myAttendancePct() = ApiResponse.ok(attendance.percentage(current.studentOrThrow().id!!))

    @GetMapping("/marks")
    fun myMarks() = ApiResponse.ok(marks.byStudent(current.studentOrThrow().id!!))

    @GetMapping("/fees")
    fun myFees() = ApiResponse.ok(fees.byStudent(current.studentOrThrow().id!!))

    @GetMapping("/exams")
    fun myExams() = ApiResponse.ok(exams.list())

    @GetMapping("/assignments")
    fun myAssignments() = ApiResponse.ok(assignments.list())

    @GetMapping("/submissions")
    fun mySubmissions() = ApiResponse.ok(assignmentSubs.findByStudentId(current.studentOrThrow().id!!))

    @GetMapping("/library")
    fun myLibrary() = ApiResponse.ok(issues.findByStudentIdAndReturnedOnIsNull(current.studentOrThrow().id!!))

    @GetMapping("/library/books")
    fun browseBooks() = ApiResponse.ok(books.findAll().map { BookDto(it.id, it.isbn, it.title, it.author, it.totalCopies, it.availableCopies) })

    @GetMapping("/notices")
    fun myNotices() = ApiResponse.ok(notices.list())
}

@RestController
@RequestMapping("/api/teacher/me")
@PreAuthorize("hasRole('TEACHER')")
class TeacherSelfController(
    private val current: CurrentUserService,
    private val teacherSvc: TeacherService,
    private val subjects: SubjectRepository,
    private val students: StudentRepository,
    private val assignments: AssignmentRepository,
    private val subs: AssignmentSubmissionRepository,
    private val exams: ExamService
) {
    @GetMapping
    fun me() = ApiResponse.ok(teacherSvc.get(current.teacherOrThrow().id!!))

    @GetMapping("/subjects")
    fun mySubjects() = ApiResponse.ok(subjects.findByTeacherId(current.teacherOrThrow().id!!))

    @GetMapping("/classes")
    fun myClasses(): ApiResponse<List<Map<String, Any?>>> {
        val t = current.teacherOrThrow()
        val classes = subjects.findByTeacherId(t.id!!)
            .mapNotNull { it.schoolClass }
            .distinctBy { it.id }
            .map { mapOf("id" to it.id, "name" to it.name, "section" to it.section, "academicYear" to it.academicYear) }
        return ApiResponse.ok(classes)
    }

    @GetMapping("/students")
    fun myStudents(): ApiResponse<List<Map<String, Any?>>> {
        val classIds = subjects.findByTeacherId(current.teacherOrThrow().id!!)
            .mapNotNull { it.schoolClass?.id }.toSet()
        val list = classIds.flatMap { students.findBySchoolClassId(it) }
            .distinctBy { it.id }
            .map { mapOf("id" to it.id, "name" to it.user?.fullName, "admissionNo" to it.admissionNo, "classId" to it.schoolClass?.id) }
        return ApiResponse.ok(list)
    }

    @GetMapping("/assignments")
    fun myAssignments() = ApiResponse.ok(assignments.findByTeacherId(current.teacherOrThrow().id!!))

    @GetMapping("/submissions/pending")
    fun pending(): ApiResponse<Int> {
        val my = assignments.findByTeacherId(current.teacherOrThrow().id!!).mapNotNull { it.id }
        val pending = my.sumOf { aid -> subs.findByAssignmentId(aid).count { it.grade.isNullOrBlank() } }
        return ApiResponse.ok(pending)
    }

    @GetMapping("/exams")
    fun upcomingExams() = ApiResponse.ok(exams.list())
}

/**
 * Secure parent self-service endpoints.
 * Parent identity is derived from JWT — never from the request.
 * All data is filtered to the authenticated parent's linked children only.
 */
@RestController
@RequestMapping("/api/parent/me")
@PreAuthorize("hasRole('PARENT')")
class ParentSelfController(
    private val current: CurrentUserService,
    private val students: StudentRepository,
    private val attendance: AttendanceService,
    private val marks: MarkService,
    private val fees: FeeService,
    private val assignments: AssignmentService,
    private val assignmentSubs: AssignmentSubmissionRepository,
    private val exams: ExamService,
    private val notices: NoticeService
) {

    private fun myChildren() =
        students.findByParentId(current.parentOrThrow().id!!)

    private fun myChildIds(): Set<Long> =
        myChildren().mapNotNull { it.id }.toSet()

    private fun childMap(s: com.school.sms.entity.Student) = mapOf(
        "id" to s.id,
        "name" to (s.user?.fullName ?: ""),
        "admissionNo" to s.admissionNo,
        "classId" to s.schoolClass?.id,
        "className" to s.schoolClass?.name,
        "section" to s.schoolClass?.section
    )

    @GetMapping
    fun me(): ApiResponse<Map<String, Any?>> {
        val p = current.parentOrThrow()
        val u = p.user
        return ApiResponse.ok(mapOf(
            "id" to p.id,
            "userId" to u?.id,
            "fullName" to u?.fullName,
            "email" to u?.email,
            "phone" to u?.phone,
            "occupation" to p.occupation,
            "address" to p.address,
            "childrenCount" to myChildren().size
        ))
    }

    @GetMapping("/children")
    fun children() = ApiResponse.ok(myChildren().map(::childMap))

    @GetMapping("/attendance")
    fun myAttendance(
        @RequestParam(required = false) studentId: Long?,
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?
    ): ApiResponse<Map<String, Any?>> {
        val ids = myChildIds()
        if (ids.isEmpty()) return ApiResponse.ok(mapOf("summary" to emptyList<Any>(), "records" to emptyList<Any>()))
        val targets = if (studentId != null) {
            if (studentId !in ids) throw com.school.sms.exception.ForbiddenException("Not your child")
            setOf(studentId)
        } else ids
        val summary = targets.map { sid ->
            mapOf(
                "studentId" to sid,
                "percentage" to attendance.percentage(sid)
            )
        }
        val records = targets.flatMap { sid ->
            attendance.byStudent(sid, from, to).map { rec -> mapOf("studentId" to sid, "record" to rec) }
        }
        return ApiResponse.ok(mapOf("summary" to summary, "records" to records))
    }

    @GetMapping("/marks")
    fun myMarks(@RequestParam(required = false) studentId: Long?): ApiResponse<List<Map<String, Any?>>> {
        val ids = myChildIds()
        val targets = if (studentId != null) {
            if (studentId !in ids) throw com.school.sms.exception.ForbiddenException("Not your child")
            listOf(studentId)
        } else ids.toList()
        val out = targets.map { sid -> mapOf("studentId" to sid, "marks" to marks.byStudent(sid)) }
        return ApiResponse.ok(out)
    }

    @GetMapping("/fees")
    fun myFees(@RequestParam(required = false) studentId: Long?): ApiResponse<List<Map<String, Any?>>> {
        val ids = myChildIds()
        val targets = if (studentId != null) {
            if (studentId !in ids) throw com.school.sms.exception.ForbiddenException("Not your child")
            listOf(studentId)
        } else ids.toList()
        val out = targets.map { sid -> mapOf("studentId" to sid, "fees" to fees.byStudent(sid)) }
        return ApiResponse.ok(out)
    }

    @GetMapping("/assignments")
    fun myAssignments(): ApiResponse<List<Map<String, Any?>>> {
        // Assignments visible to each child + pending status per child
        val all = assignments.list()
        val out = myChildren().map { s ->
            val subs = assignmentSubs.findByStudentId(s.id!!).mapNotNull { it.assignment?.id }.toSet()
            val pending = all.filter { it.id !in subs }
            mapOf(
                "studentId" to s.id,
                "studentName" to (s.user?.fullName ?: ""),
                "pending" to pending,
                "submittedCount" to subs.size,
                "totalCount" to all.size
            )
        }
        return ApiResponse.ok(out)
    }

    @GetMapping("/exams")
    fun upcomingExams() = ApiResponse.ok(exams.list())

    @GetMapping("/notices")
    fun myNotices() = ApiResponse.ok(notices.list())
}
