package com.school.sms.reports

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.school.sms.exception.NotFoundException
import com.school.sms.repository.*
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter

enum class ReportFormat { PDF, EXCEL, CSV }

@Service
class ReportService(
    private val students: StudentRepository,
    private val marks: MarkRepository,
    private val attendance: AttendanceRepository,
    private val fees: FeeRepository,
    private val exams: ExamRepository,
    private val teachers: TeacherRepository,
    private val subjects: SubjectRepository
) {
    fun studentReportCard(studentId: Long, format: ReportFormat): ByteArray {
        val s = students.findById(studentId).orElseThrow { NotFoundException("Student") }
        val ms = marks.findByStudentId(studentId)
        val headers = listOf("Exam", "Subject", "Marks", "Max", "Grade")
        val rows = ms.map { listOf(it.exam?.name.orEmpty(), it.subject?.name.orEmpty(), it.marksObtained.toPlainString(), it.maxMarks.toPlainString(), it.grade.orEmpty()) }
        return render("Student Report Card - ${s.user?.fullName} (${s.admissionNo})", headers, rows, format)
    }
    fun attendanceReport(classId: Long?, format: ReportFormat): ByteArray {
        val list = if (classId == null) attendance.findAll() else students.findBySchoolClassId(classId).flatMap { attendance.findByStudentId(it.id!!) }
        val headers = listOf("Student", "Date", "Status", "Remarks")
        val rows = list.map { listOf(it.student?.user?.fullName.orEmpty(), it.date.toString(), it.status.name, it.remarks.orEmpty()) }
        return render("Attendance Report", headers, rows, format)
    }
    fun feeReport(format: ReportFormat): ByteArray {
        val all = fees.findAll()
        val headers = listOf("Student", "Term", "Amount", "Paid", "Due", "Status")
        val rows = all.map { listOf(it.student?.user?.fullName.orEmpty(), it.term, it.amount.toPlainString(), it.amountPaid.toPlainString(), it.dueDate?.toString().orEmpty(), it.status.name) }
        return render("Fee Report", headers, rows, format)
    }
    fun examReport(examId: Long, format: ReportFormat): ByteArray {
        val e = exams.findById(examId).orElseThrow { NotFoundException("Exam") }
        val ms = marks.findByExamId(examId)
        val headers = listOf("Student", "Subject", "Marks", "Max", "Grade")
        val rows = ms.map { listOf(it.student?.user?.fullName.orEmpty(), it.subject?.name.orEmpty(), it.marksObtained.toPlainString(), it.maxMarks.toPlainString(), it.grade.orEmpty()) }
        return render("Exam Report - ${e.name}", headers, rows, format)
    }
    fun teacherWorkloadReport(format: ReportFormat): ByteArray {
        val ts = teachers.findAll()
        val headers = listOf("Teacher", "Employee Code", "Subjects", "Classes")
        val rows = ts.map { t ->
            val subs = subjects.findByTeacherId(t.id!!)
            listOf(t.user?.fullName.orEmpty(), t.employeeCode, subs.size.toString(),
                subs.mapNotNull { it.schoolClass?.name }.distinct().joinToString(","))
        }
        return render("Teacher Workload Report", headers, rows, format)
    }

    private fun render(title: String, headers: List<String>, rows: List<List<String>>, format: ReportFormat): ByteArray = when (format) {
        ReportFormat.PDF -> pdf(title, headers, rows)
        ReportFormat.EXCEL -> excel(title, headers, rows)
        ReportFormat.CSV -> csv(headers, rows)
    }

    private fun pdf(title: String, headers: List<String>, rows: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        PdfDocument(PdfWriter(out)).use { pdf ->
            Document(pdf).use { doc ->
                doc.add(Paragraph(title).setBold().setFontSize(16f))
                val table = Table(headers.size.coerceAtLeast(1)).useAllAvailableWidth()
                headers.forEach { table.addHeaderCell(it) }
                rows.forEach { r -> r.forEach { table.addCell(it) } }
                doc.add(table)
            }
        }
        return out.toByteArray()
    }
    private fun excel(title: String, headers: List<String>, rows: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet(title.take(31))
            val h = sheet.createRow(0); headers.forEachIndexed { i, v -> h.createCell(i).setCellValue(v) }
            rows.forEachIndexed { i, r ->
                val row = sheet.createRow(i + 1); r.forEachIndexed { j, v -> row.createCell(j).setCellValue(v) }
            }
            wb.write(out)
        }
        return out.toByteArray()
    }
    private fun csv(headers: List<String>, rows: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        CSVPrinter(OutputStreamWriter(out, Charsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(*headers.toTypedArray()).build()).use { p ->
            rows.forEach { p.printRecord(it) }
        }
        return out.toByteArray()
    }
}

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','TEACHER','ACCOUNTANT')")
class ReportController(private val service: ReportService) {

    @GetMapping("/student/{id}")
    fun studentCard(@PathVariable id: Long, @RequestParam(defaultValue = "PDF") format: ReportFormat) =
        respond(service.studentReportCard(id, format), "student-$id", format)

    @GetMapping("/attendance")
    fun attendance(@RequestParam(required = false) classId: Long?, @RequestParam(defaultValue = "PDF") format: ReportFormat) =
        respond(service.attendanceReport(classId, format), "attendance", format)

    @GetMapping("/fees")
    fun feeReport(@RequestParam(defaultValue = "PDF") format: ReportFormat) =
        respond(service.feeReport(format), "fees", format)

    @GetMapping("/exam/{id}")
    fun examReport(@PathVariable id: Long, @RequestParam(defaultValue = "PDF") format: ReportFormat) =
        respond(service.examReport(id, format), "exam-$id", format)

    @GetMapping("/teacher-workload")
    fun workload(@RequestParam(defaultValue = "PDF") format: ReportFormat) =
        respond(service.teacherWorkloadReport(format), "teacher-workload", format)

    private fun respond(bytes: ByteArray, base: String, fmt: ReportFormat): ResponseEntity<ByteArray> {
        val (ext, mime) = when (fmt) {
            ReportFormat.PDF -> "pdf" to MediaType.APPLICATION_PDF_VALUE
            ReportFormat.EXCEL -> "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ReportFormat.CSV -> "csv" to "text/csv"
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$base.$ext\"")
            .contentType(MediaType.parseMediaType(mime))
            .body(bytes)
    }
}
