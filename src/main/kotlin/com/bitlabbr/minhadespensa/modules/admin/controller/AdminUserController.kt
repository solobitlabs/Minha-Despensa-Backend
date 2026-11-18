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
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminAuditLogDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminBlockUserRequest
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminDashboardSummaryDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminPagedResponseDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminUserDetailDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminUserSummaryDto
import com.bitlabbr.minhadespensa.modules.admin.service.AdminAuditService
import com.bitlabbr.minhadespensa.modules.admin.service.AdminUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin - Usuários & Auditoria", description = "Gestão de usuários, sessões, KPIs e auditoria (requer SYSTEM_ADMIN)")
@SecurityRequirement(name = "bearerAuth")
class AdminUserController(
    private val adminUserService: AdminUserService,
    private val adminAuditService: AdminAuditService
) {

    @GetMapping("/dashboard")
    @Operation(summary = "Consultar KPIs consolidados do sistema")
    fun getDashboard(): ApiResponse<AdminDashboardSummaryDto> {
        return ApiResponse.ok(adminUserService.getDashboardSummary())
    }

    @GetMapping("/users")
    @Operation(summary = "Listar usuários com filtro por nome/e-mail e status de bloqueio")
    fun listUsers(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) blocked: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): ApiResponse<AdminPagedResponseDto<AdminUserSummaryDto>> {
        return ApiResponse.ok(adminUserService.listUsers(query, blocked, page, size))
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Obter detalhes de um usuário e suas casas compartilhadas")
    fun getUserDetail(@PathVariable id: String): ApiResponse<AdminUserDetailDto> {
        return ApiResponse.ok(adminUserService.getUserDetail(id))
    }

    @PatchMapping("/users/{id}/block")
    @Operation(summary = "Bloquear ou desbloquear um usuário")
    fun setUserBlocked(
        @PathVariable id: String,
        @Valid @RequestBody request: AdminBlockUserRequest,
        @AuthenticationPrincipal jwt: Jwt?,
        httpRequest: HttpServletRequest
    ): ApiResponse<AdminUserSummaryDto> {
        val updated = adminUserService.setUserBlocked(
            userId = id,
            request = request,
            adminUsername = jwt?.subject ?: "admin",
            clientIp = resolveClientIp(httpRequest)
        )
        return ApiResponse.ok(updated, if (request.blocked) "Usuário bloqueado." else "Usuário desbloqueado.")
    }

    @PostMapping("/users/{id}/revoke-sessions")
    @Operation(summary = "Revogar todos os refresh tokens ativos de um usuário")
    fun revokeSessions(
        @PathVariable id: String,
        @AuthenticationPrincipal jwt: Jwt?,
        httpRequest: HttpServletRequest
    ): ApiResponse<Map<String, Int>> {
        val revoked = adminUserService.revokeUserSessions(
            userId = id,
            adminUsername = jwt?.subject ?: "admin",
            clientIp = resolveClientIp(httpRequest)
        )
        return ApiResponse.ok(mapOf("revokedTokens" to revoked), "Sessões do usuário revogadas.")
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Listar registros recentes da trilha de auditoria administrativa")
    fun listAuditLogs(
        @RequestParam(defaultValue = "50") limit: Int
    ): ApiResponse<List<AdminAuditLogDto>> {
        return ApiResponse.ok(adminAuditService.listRecent(limit))
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(",").first().trim()
        }
        return request.remoteAddr ?: "unknown"
    }
}
