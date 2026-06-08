package com.school.sms

import com.fasterxml.jackson.databind.ObjectMapper
import com.school.sms.dto.LoginRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest @Autowired constructor(
    val mvc: MockMvc,
    val mapper: ObjectMapper
) {
    @Test
    fun `super admin can login`() {
        val body = mapper.writeValueAsString(LoginRequest("superadmin", "Admin@123"))
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isString)
    }

    @Test
    fun `invalid login fails`() {
        val body = mapper.writeValueAsString(LoginRequest("nobody", "wrong"))
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
    }
}
