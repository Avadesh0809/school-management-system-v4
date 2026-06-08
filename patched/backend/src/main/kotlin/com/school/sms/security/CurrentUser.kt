package com.school.sms.security

import com.school.sms.entity.Parent
import com.school.sms.entity.Student
import com.school.sms.entity.Teacher
import com.school.sms.entity.User
import com.school.sms.exception.ForbiddenException
import com.school.sms.exception.NotFoundException
import com.school.sms.repository.ParentRepository
import com.school.sms.repository.StudentRepository
import com.school.sms.repository.TeacherRepository
import com.school.sms.repository.UserRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

/**
 * Resolves the authenticated user and their domain identities (student/teacher/parent).
 * NEVER trust IDs coming from the frontend — derive them from the JWT subject.
 */
@Service
class CurrentUserService(
    private val users: UserRepository,
    private val students: StudentRepository,
    private val teachers: TeacherRepository,
    private val parents: ParentRepository
) {
    fun username(): String = SecurityContextHolder.getContext().authentication?.name
        ?: throw ForbiddenException("Not authenticated")

    fun roles(): Set<String> = SecurityContextHolder.getContext().authentication
        ?.authorities?.map { it.authority.removePrefix("ROLE_") }?.toSet() ?: emptySet()

    fun user(): User = users.findByUsername(username())
        .orElseThrow { NotFoundException("Current user") }

    fun studentOrThrow(): Student {
        val uid = user().id ?: throw ForbiddenException("Not a student")
        return students.findByUserId(uid)
            .orElseThrow { ForbiddenException("Not linked to a student record") }
    }

    fun teacherOrThrow(): Teacher {
        val uid = user().id ?: throw ForbiddenException("Not a teacher")
        return teachers.findByUserId(uid)
            .orElseThrow { ForbiddenException("Not linked to a teacher record") }
    }

    fun parentOrThrow(): Parent {
        val uid = user().id ?: throw ForbiddenException("Not a parent")
        return parents.findByUserId(uid)
            .orElseThrow { ForbiddenException("Not linked to a parent record") }
    }

    fun hasAnyRole(vararg names: String): Boolean {
        val r = roles()
        return names.any { it in r }
    }

    fun isStudent() = "STUDENT" in roles()
    fun isParent() = "PARENT" in roles()
    fun isTeacher() = "TEACHER" in roles()
    fun isAdmin() = hasAnyRole("ADMIN", "SUPER_ADMIN")
}
