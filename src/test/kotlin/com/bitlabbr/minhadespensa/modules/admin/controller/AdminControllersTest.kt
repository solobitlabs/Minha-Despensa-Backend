/*
 *   Copyright (c) 2026 Willian Santos
 *
 *   This work is licensed under the Creative Commons
 *   Attribution-NonCommercial 4.0 International License (CC BY-NC 4.0).
 *
 *   You are free to:
 *     - Share  — copy and redistribute the material in any medium or format
 *     - Adapt  — remix, transform, and build upon the material
 *
 *   Under the following terms:
 *     - Attribution    — You must give appropriate credit, provide a link to
 *                        the license, and indicate if changes were made.
 *     - NonCommercial  — You may not use the material for commercial purposes.
 *
 *   Owner rights:
 *     - Willian Santos retains all commercial rights.
 *    - The copyright holder may use, sell, sublicense, or relicense this
 *       work under different terms at any time.
 *
 *   Full license: https://creativecommons.org/licenses/by-nc/4.0/legalcode
 */

package com.bitlabbr.minhadespensa.modules.admin.controller

import com.bitlabbr.minhadespensa.common.exception.GlobalExceptionHandler
import com.bitlabbr.minhadespensa.common.exception.TooManyRequestsException
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminDashboardSummaryDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminPagedResponseDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminUserSummaryDto
import com.bitlabbr.minhadespensa.modules.admin.security.LoginRateLimiter
import com.bitlabbr.minhadespensa.modules.admin.service.AdminAuditService
import com.bitlabbr.minhadespensa.modules.admin.service.AdminCatalogService
import com.bitlabbr.minhadespensa.modules.admin.service.AdminUserService
import com.bitlabbr.minhadespensa.modules.auth.config.SecurityConfig
import com.bitlabbr.minhadespensa.modules.catalog.domain.MeasureUnit
import com.bitlabbr.minhadespensa.modules.catalog.dto.GlobalProductResponseDto
import com.bitlabbr.minhadespensa.modules.support.dto.SupportAdminLoginResponse
import com.bitlabbr.minhadespensa.modules.support.service.SupportAuthService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(
    AdminAuthController::class,
    AdminCatalogController::class,
    AdminUserController::class,
    AdminWebViewController::class
)
@Import(GlobalExceptionHandler::class, SecurityConfig::class)
class AdminControllersTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var supportAuthService: SupportAuthService

    @MockitoBean
    private lateinit var loginRateLimiter: LoginRateLimiter

    @MockitoBean
    private lateinit var adminAuditService: AdminAuditService

    @MockitoBean
    private lateinit var adminCatalogService: AdminCatalogService

    @MockitoBean
    private lateinit var adminUserService: AdminUserService

    private fun adminJwt() = jwt()
        .jwt { it.subject("admin").claim("role", "SYSTEM_ADMIN") }
        .authorities(SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))

    private fun regularUserJwt() = jwt()
        .jwt { it.subject("user-123") }

    @Test
    fun `GET admin page should return HTML with strict security headers`() {
        mockMvc.perform(get("/admin"))
            .andExpect(status().isOk)
            .andExpect(header().exists("Content-Security-Policy"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
    }

    @Test
    fun `POST admin login should return 200 on valid credentials and 429 when rate limited`() {
        whenever(supportAuthService.login(any())).thenReturn(
            SupportAdminLoginResponse(
                accessToken = "jwt-admin-token",
                username = "admin",
                expiresInSeconds = 1800L
            )
        )

        mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"secret"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").value("jwt-admin-token"))
            .andExpect(jsonPath("$.data.role").value("SYSTEM_ADMIN"))

        whenever(loginRateLimiter.checkAllowed(any())).thenThrow(TooManyRequestsException("Rate limited"))

        mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"wrong"}""")
        )
            .andExpect(status().isTooManyRequests)
    }

    @Test
    fun `GET admin products should return 401 without token and 403 for non-admin user`() {
        mockMvc.perform(get("/api/v1/admin/products"))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            get("/api/v1/admin/products").with(regularUserJwt())
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET and POST admin products should succeed with SYSTEM_ADMIN role`() {
        val dto = GlobalProductResponseDto(
            id = "g-1",
            ean = "7891000100103",
            name = "Leite Moça",
            brand = "Nestlé",
            category = "Laticínios",
            measureUnit = MeasureUnit.GRAM,
            netWeight = 395.0,
            source = "ADMIN_CURATED",
            occurrenceCount = 1L,
            imageHash = null,
            isVerified = true,
            updatedAt = 1000L
        )
        whenever(adminCatalogService.listProducts(anyOrNull(), anyOrNull(), anyOrNull(), eq(0), eq(25)))
            .thenReturn(AdminPagedResponseDto(listOf(dto), 0, 25, 1L, 1))
        whenever(adminCatalogService.createProduct(any(), eq("admin"), anyOrNull())).thenReturn(dto)

        mockMvc.perform(get("/api/v1/admin/products").with(adminJwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].ean").value("7891000100103"))
            .andExpect(jsonPath("$.data.items[0].isVerified").value(true))

        mockMvc.perform(
            post("/api/v1/admin/products")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ean":"7891000100103","name":"Leite Moça","brand":"Nestlé","measureUnit":"GRAM","netWeight":395.0}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.source").value("ADMIN_CURATED"))
    }

    @Test
    fun `GET dashboard and PATCH block user should require SYSTEM_ADMIN`() {
        whenever(adminUserService.getDashboardSummary()).thenReturn(
            AdminDashboardSummaryDto(10, 5, 2, 20, 1, 15)
        )
        whenever(adminUserService.setUserBlocked(eq("user-1"), any(), eq("admin"), anyOrNull())).thenReturn(
            AdminUserSummaryDto("user-1", "João", "joao@test.com", null, 1, true, "Abuso", 1L, 2L)
        )

        mockMvc.perform(get("/api/v1/admin/dashboard").with(regularUserJwt()))
            .andExpect(status().isForbidden)

        mockMvc.perform(get("/api/v1/admin/dashboard").with(adminJwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalGlobalProducts").value(10))

        mockMvc.perform(
            patch("/api/v1/admin/users/user-1/block")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"blocked":true,"reason":"Abuso"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isBlocked").value(true))
    }
}
