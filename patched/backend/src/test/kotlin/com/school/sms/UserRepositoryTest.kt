package com.school.sms

import com.school.sms.entity.Role
import com.school.sms.entity.User
import com.school.sms.repository.RoleRepository
import com.school.sms.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryTest @Autowired constructor(
    val users: UserRepository,
    val roles: RoleRepository,
    val encoder: PasswordEncoder
) {
    @Test
    fun `default super admin is seeded`() {
        assertThat(users.findByUsername("superadmin")).isPresent
    }

    @Test
    fun `can create user with role`() {
        val role = roles.findByName("TEACHER").orElseGet { roles.save(Role("TEACHER")) }
        val u = User("t1", "t1@x.com", encoder.encode("pass1234"), "T One").apply { this.roles = mutableSetOf(role) }
        val saved = users.save(u)
        assertThat(saved.id).isNotNull()
        assertThat(saved.roles).hasSize(1)
    }
}
