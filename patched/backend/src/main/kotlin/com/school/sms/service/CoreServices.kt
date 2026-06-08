package com.school.sms.service

import com.school.sms.audit.Auditable
import com.school.sms.dto.*
import com.school.sms.entity.*
import com.school.sms.exception.*
import com.school.sms.rbac.Roles
import com.school.sms.repository.*
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

fun <T> Page<T>.toPaged(mapper: (T) -> Any) = PagedResponse(content.map { mapper(it) } as List<Any>, number, size, totalElements, totalPages)

@Service @Transactional
class UserService(
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val encoder: PasswordEncoder
) {
    fun toDto(u: User) = UserDto(u.id, u.username, u.email, u.fullName, u.phone, u.profileImagePath, u.enabled, u.roles.map { it.name })

    @Cacheable("users", key = "#id")
    fun get(id: Long) = users.findById(id).orElseThrow { NotFoundException("User $id") }.let(::toDto)

    fun list(pageable: Pageable, q: String?): PagedResponse<UserDto> {
        val page = if (q.isNullOrBlank()) users.findAll(pageable)
        else users.findAll({ root, _, cb -> cb.or(
            cb.like(cb.lower(root.get("username")), "%${q.lowercase()}%"),
            cb.like(cb.lower(root.get("email")), "%${q.lowercase()}%"),
            cb.like(cb.lower(root.get("fullName")), "%${q.lowercase()}%")
        )}, pageable)
        return PagedResponse(page.content.map(::toDto), page.number, page.size, page.totalElements, page.totalPages)
    }

    @Auditable(action = "CREATE_USER", entity = "User")
    @CacheEvict(value = ["users"], allEntries = true)
    fun create(req: CreateUserRequest): UserDto {
        if (users.existsByUsername(req.username)) throw ConflictException("Username exists")
        if (users.existsByEmail(req.email)) throw ConflictException("Email exists")
        val u = User(req.username, req.email, encoder.encode(req.password), req.fullName, req.phone)
        u.roles = resolveRoles(req.roles.ifEmpty { setOf(Roles.STUDENT) })
        return toDto(users.save(u))
    }

    @CacheEvict(value = ["users"], key = "#id")
    @Auditable(action = "UPDATE_USER", entity = "User")
    fun update(id: Long, req: UpdateUserRequest): UserDto {
        val u = users.findById(id).orElseThrow { NotFoundException("User $id") }
        req.email?.let { u.email = it }
        req.fullName?.let { u.fullName = it }
        req.phone?.let { u.phone = it }
        req.enabled?.let { u.enabled = it }
        req.roles?.let { u.roles = resolveRoles(it) }
        return toDto(users.save(u))
    }

    @CacheEvict(value = ["users"], key = "#id")
    @Auditable(action = "DELETE_USER", entity = "User")
    fun delete(id: Long) { users.deleteById(id) }

    fun changePassword(username: String, req: ChangePasswordRequest) {
        val u = users.findByUsername(username).orElseThrow { NotFoundException("User") }
        if (!encoder.matches(req.oldPassword, u.passwordHash)) throw BadRequestException("Old password incorrect")
        u.passwordHash = encoder.encode(req.newPassword); users.save(u)
    }

    fun setProfileImage(username: String, path: String) {
        val u = users.findByUsername(username).orElseThrow { NotFoundException("User") }
        u.profileImagePath = path; users.save(u)
    }

    @Auditable(action = "UPDATE_PROFILE", entity = "User")
    fun updateOwnProfile(username: String, req: UpdateProfileRequest): UserDto {
        val u = users.findByUsername(username).orElseThrow { NotFoundException("User") }
        req.username?.let {
            if (it != u.username) {
                if (users.existsByUsername(it)) throw ConflictException("Username already in use")
                u.username = it
            }
        }
        req.email?.let {
            if (it != u.email) {
                if (users.existsByEmail(it)) throw ConflictException("Email already in use")
                u.email = it
            }
        }
        req.fullName?.let { u.fullName = it }
        req.phone?.let { u.phone = it }
        return toDto(users.save(u))
    }

    private fun resolveRoles(names: Set<String>): MutableSet<Role> =
        names.map { roles.findByName(it).orElseThrow { BadRequestException("Role $it not found") } }.toMutableSet()
}

@Service @Transactional
class AuthService(
    private val users: UserRepository,
    private val refresh: RefreshTokenRepository,
    private val encoder: PasswordEncoder,
    private val jwt: com.school.sms.security.JwtTokenProvider,
    private val userService: UserService,
    private val email: com.school.sms.email.EmailService,
    private val resetTokens: com.school.sms.repository.PasswordResetTokenRepository
) {
    fun login(req: LoginRequest, ip: String?): LoginResponse {
        val u = users.findByUsername(req.username).orElseGet {
            users.findByEmail(req.username).orElseThrow { UnauthorizedException("Invalid credentials") }
        }
        if (!u.enabled || !encoder.matches(req.password, u.passwordHash)) throw UnauthorizedException("Invalid credentials")
        u.lastLoginAt = LocalDateTime.now(); users.save(u)
        val access = jwt.generateAccessToken(u)
        val rt = refresh.save(RefreshToken(UUID.randomUUID().toString(), u))
        email.sendLoginAlert(u.email, u.username, ip)
        return LoginResponse(access, rt.token, userService.get(u.id!!))
    }

    fun refresh(token: String): LoginResponse {
        val rt = refresh.findByToken(token).orElseThrow { UnauthorizedException("Invalid refresh") }
        if (rt.revoked || rt.expiresAt.isBefore(LocalDateTime.now())) throw UnauthorizedException("Expired refresh")
        val u = rt.user!!
        rt.revoked = true; refresh.save(rt)
        val access = jwt.generateAccessToken(u)
        val newRt = refresh.save(RefreshToken(UUID.randomUUID().toString(), u))
        return LoginResponse(access, newRt.token, userService.get(u.id!!))
    }

    fun logout(token: String) {
        refresh.findByToken(token).ifPresent { it.revoked = true; refresh.save(it) }
    }

    /** Phase 2: forgot/reset password. Always succeeds silently to avoid email enumeration. */
    fun forgotPassword(emailAddr: String) {
        val u = users.findByEmail(emailAddr).orElse(null) ?: return
        val token = UUID.randomUUID().toString()
        resetTokens.save(com.school.sms.entity.PasswordResetToken(
            token = token,
            user = u,
            expiresAt = LocalDateTime.now().plusHours(1),
            used = false
        ))
        email.sendPasswordReset(u.email, u.fullName, token)
    }

    fun resetPassword(token: String, newPassword: String) {
        val prt = resetTokens.findByToken(token).orElseThrow { BadRequestException("Invalid or expired reset token") }
        if (prt.used) throw BadRequestException("Reset token already used")
        if (prt.expiresAt.isBefore(LocalDateTime.now())) throw BadRequestException("Reset token has expired")
        val u = prt.user ?: throw BadRequestException("Invalid reset token")
        u.passwordHash = encoder.encode(newPassword)
        users.save(u)
        prt.used = true
        resetTokens.save(prt)
        // Revoke all refresh tokens for the user as a safety measure
        refresh.deleteByUserId(u.id!!)
    }
}
