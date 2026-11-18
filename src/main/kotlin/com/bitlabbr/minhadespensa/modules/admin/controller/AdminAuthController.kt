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

import com.bitlabbr.minhadespensa.common.dto.ApiResponse
import com.bitlabbr.minhadespensa.common.exception.UnauthorizedException
import com.bitlabbr.minhadespensa.modules.admin.security.LoginRateLimiter
import com.bitlabbr.minhadespensa.modules.admin.service.AdminAuditService
import com.bitlabbr.minhadespensa.modules.support.dto.SupportAdminLoginRequest
import com.bitlabbr.minhadespensa.modules.support.dto.SupportAdminLoginResponse
import com.bitlabbr.minhadespensa.modules.support.service.SupportAuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/auth")
@Tag(name = "Admin - Autenticação", description = "Autenticação administrativa com proteção contra brute-force e auditoria")
class AdminAuthController(
    private val supportAuthService: SupportAuthService,
    private val loginRateLimiter: LoginRateLimiter,
    private val adminAuditService: AdminAuditService
) {

    @PostMapping("/login")
    @Operation(summary = "Login administrativo", description = "Autentica o administrador do sistema emitindo JWT com papel SYSTEM_ADMIN.")
    fun login(
        @Valid @RequestBody request: SupportAdminLoginRequest,
        httpRequest: HttpServletRequest
    ): ApiResponse<SupportAdminLoginResponse> {
        val clientIp = resolveClientIp(httpRequest)
        val rateLimitKey = "$clientIp:${request.username.trim().lowercase()}"
        loginRateLimiter.checkAllowed(rateLimitKey)

        return try {
            val response = supportAuthService.login(request)
            loginRateLimiter.reset(rateLimitKey)
            adminAuditService.record(
                adminUsername = response.username,
                action = "ADMIN_LOGIN_SUCCESS",
                targetType = "ADMIN_SESSION",
                targetId = response.username,
                details = "Admin console login",
                clientIp = clientIp
            )
            ApiResponse.ok(response, "Autenticação administrativa realizada com sucesso.")
        } catch (ex: UnauthorizedException) {
            loginRateLimiter.recordFailure(rateLimitKey)
            throw ex
        }
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(",").first().trim()
        }
        return request.remoteAddr ?: "unknown"
    }
}
