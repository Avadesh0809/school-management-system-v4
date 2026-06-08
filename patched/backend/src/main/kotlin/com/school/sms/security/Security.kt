package com.school.sms.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.school.sms.dto.ApiResponse
import com.school.sms.entity.User
import com.school.sms.repository.UserRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User as SpringUser
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Date
import javax.crypto.SecretKey

@Service
class CustomUserDetailsService(private val users: UserRepository) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val u = users.findByUsername(username).orElseGet {
            users.findByEmail(username).orElseThrow { UsernameNotFoundException("User not found: $username") }
        }
        if (!u.enabled) throw UsernameNotFoundException("User disabled")
        val auths = u.roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }
        return SpringUser(u.username, u.passwordHash, auths)
    }
}

@Component
class JwtTokenProvider(
    @Value("\${app.jwt.secret}") private val secret: String,
    @Value("\${app.jwt.access-token-minutes}") private val accessMinutes: Long,
) {
    private val key: SecretKey by lazy { Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret)) }

    fun generateAccessToken(user: User): String {
        val now = Date()
        return Jwts.builder()
            .subject(user.username)
            .claim("uid", user.id)
            .claim("roles", user.roles.map { it.name })
            .issuedAt(now)
            .expiration(Date(now.time + accessMinutes * 60_000))
            .signWith(key)
            .compact()
    }

    fun parse(token: String) = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
    fun isValid(token: String): Boolean = runCatching { parse(token); true }.getOrDefault(false)
    fun getUsername(token: String): String = parse(token).subject
}

class JwtAuthFilter(
    private val jwt: JwtTokenProvider,
    private val uds: CustomUserDetailsService
) : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val header = req.getHeader("Authorization")
        if (header?.startsWith("Bearer ") == true) {
            val token = header.substring(7)
            if (jwt.isValid(token)) {
                runCatching {
                    val username = jwt.getUsername(token)
                    val user = uds.loadUserByUsername(username)
                    val auth = UsernamePasswordAuthenticationToken(user, null, user.authorities)
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        }
        chain.doFilter(req, res)
    }
}

@Component
class RestAuthEntryPoint(private val mapper: ObjectMapper) : AuthenticationEntryPoint {
    override fun commence(req: HttpServletRequest, res: HttpServletResponse, e: AuthenticationException) {
        res.status = HttpStatus.UNAUTHORIZED.value()
        res.contentType = MediaType.APPLICATION_JSON_VALUE
        res.writer.write(mapper.writeValueAsString(ApiResponse.fail("Unauthorized: ${e.message}")))
    }
}

@Component
class RestAccessDeniedHandler(private val mapper: ObjectMapper) : AccessDeniedHandler {
    override fun handle(req: HttpServletRequest, res: HttpServletResponse, e: AccessDeniedException) {
        res.status = HttpStatus.FORBIDDEN.value()
        res.contentType = MediaType.APPLICATION_JSON_VALUE
        res.writer.write(mapper.writeValueAsString(ApiResponse.fail("Access denied")))
    }
}

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val uds: CustomUserDetailsService,
    private val jwt: JwtTokenProvider,
    private val entryPoint: RestAuthEntryPoint,
    private val denied: RestAccessDeniedHandler
) {
    @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
    @Bean fun authManager(cfg: AuthenticationConfiguration): AuthenticationManager = cfg.authenticationManager
    @Bean fun authProvider(): DaoAuthenticationProvider = DaoAuthenticationProvider().apply {
        setUserDetailsService(uds); setPasswordEncoder(passwordEncoder())
    }

    @Bean
    fun cors(): UrlBasedCorsConfigurationSource {
        val cfg = CorsConfiguration().apply {
            // Allow any localhost / 127.0.0.1 port for dev (IntelliJ 63342, Live Server 5500, Vite 5173, nginx 80/8081, etc.)
            addAllowedOriginPattern("http://localhost:*")
            addAllowedOriginPattern("http://127.0.0.1:*")
            addAllowedOriginPattern("https://localhost:*")
            addAllowedHeader("*"); addAllowedMethod("*"); allowCredentials = true
            addExposedHeader("Authorization")
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", cfg) }
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(cors()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                    "/actuator/health", "/files/public/**", "/error").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied) }
            .authenticationProvider(authProvider())
            .addFilterBefore(JwtAuthFilter(jwt, uds), UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
