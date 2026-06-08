package com.school.sms.config

import com.school.sms.entity.*
import com.school.sms.rbac.Permissions
import com.school.sms.rbac.Roles
import com.school.sms.repository.*
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DataSeeder(
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val perms: PermissionRepository,
    private val rp: RolePermissionRepository,
    private val encoder: PasswordEncoder
) : CommandLineRunner {

    @Transactional
    override fun run(vararg args: String) {
        // Idempotent permission seeding. Catch race / collation duplicates.
        Permissions.ALL.forEach { name ->
            if (perms.findByName(name).isEmpty) {
                runCatching { perms.save(Permission(name)) }
            }
        }
        // Idempotent role seeding. MySQL's default collation is case-insensitive,
        // so look up case-insensitively before inserting to avoid duplicate-key errors
        // when rows like 'admin' or 'student' already exist in the DB.
        Roles.ALL.forEach { name ->
            val existing = roles.findAll().firstOrNull { it.name.equals(name, ignoreCase = true) }
            val r = existing ?: runCatching { roles.save(Role(name, "$name role")) }
                .getOrElse { roles.findAll().first { it.name.equals(name, ignoreCase = true) } }
            if (name.equals(Roles.SUPER_ADMIN, ignoreCase = true) && rp.findByRoleId(r.id!!).isEmpty()) {
                Permissions.ALL.forEach { pn ->
                    perms.findByName(pn).ifPresent { p ->
                        runCatching { rp.save(RolePermission(r, p)) }
                    }
                }
            }
        }
        if (!users.existsByUsername("superadmin")) {
            roles.findAll().firstOrNull { it.name.equals(Roles.SUPER_ADMIN, ignoreCase = true) }?.let { superRole ->
                val u = User("superadmin", "avadeshumaraiya7879@gmail.com", encoder.encode("Avadesh@123"), "Super Admin")
                u.roles = mutableSetOf(superRole)
                users.save(u)
            }
        }
        if (!users.existsByUsername("admin")) {
            roles.findAll().firstOrNull { it.name.equals(Roles.ADMIN, ignoreCase = true) }?.let { adminRole ->
                val u = User("admin", "admin@school.local", encoder.encode("Admin@123"), "Admin User")
                u.roles = mutableSetOf(adminRole)
                users.save(u)
            }
        }
    }
}
