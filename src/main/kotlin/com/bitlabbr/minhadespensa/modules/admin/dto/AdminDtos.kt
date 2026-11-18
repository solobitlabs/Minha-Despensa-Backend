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

package com.bitlabbr.minhadespensa.modules.admin.dto

import com.bitlabbr.minhadespensa.modules.admin.entity.AdminAuditLogEntity
import com.bitlabbr.minhadespensa.modules.catalog.domain.MeasureUnit
import com.bitlabbr.minhadespensa.modules.catalog.dto.GlobalProductResponseDto
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class AdminDashboardSummaryDto(
    val totalGlobalProducts: Long,
    val verifiedGlobalProducts: Long,
    val productsWithPhoto: Long,
    val totalUsers: Long,
    val blockedUsers: Long,
    val totalHouseholds: Long
)

data class AdminPagedResponseDto<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class AdminCreateGlobalProductRequest(
    @field:NotBlank(message = "O código EAN é obrigatório.")
    @field:Size(min = 8, max = 14, message = "O EAN deve conter entre 8 e 14 dígitos.")
    val ean: String,

    @field:NotBlank(message = "O nome do produto é obrigatório.")
    @field:Size(max = 255, message = "O nome não pode ultrapassar 255 caracteres.")
    val name: String,

    @field:Size(max = 80, message = "A marca não pode ultrapassar 80 caracteres.")
    val brand: String? = null,

    @field:Size(max = 100, message = "A categoria não pode ultrapassar 100 caracteres.")
    val category: String = "Outros",

    val measureUnit: MeasureUnit = MeasureUnit.UNIT,

    @field:Positive(message = "O peso líquido deve ser maior que zero.")
    val netWeight: Double = 1.0,

    val isVerified: Boolean = true
)

data class AdminUpdateGlobalProductRequest(
    @field:NotBlank(message = "O nome do produto é obrigatório.")
    @field:Size(max = 255, message = "O nome não pode ultrapassar 255 caracteres.")
    val name: String,

    @field:Size(max = 80, message = "A marca não pode ultrapassar 80 caracteres.")
    val brand: String? = null,

    @field:Size(max = 100, message = "A categoria não pode ultrapassar 100 caracteres.")
    val category: String = "Outros",

    val measureUnit: MeasureUnit = MeasureUnit.UNIT,

    @field:Positive(message = "O peso líquido deve ser maior que zero.")
    val netWeight: Double = 1.0,

    val isVerified: Boolean = true
)

data class AdminProductMediaUploadResponseDto(
    val ean: String,
    val sha256Hash: String,
    val contentType: String,
    val sizeBytes: Int
)

data class AdminUserSummaryDto(
    val id: String,
    val name: String,
    val email: String,
    val defaultHouseholdId: String?,
    val householdCount: Int,
    val isBlocked: Boolean,
    val blockedReason: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class AdminUserDetailDto(
    val id: String,
    val name: String,
    val email: String,
    val defaultHouseholdId: String?,
    val isBlocked: Boolean,
    val blockedReason: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val households: List<AdminUserHouseholdDto>
)

data class AdminUserHouseholdDto(
    val householdId: String,
    val householdName: String,
    val role: String,
    val inviteCode: String
)

data class AdminBlockUserRequest(
    val blocked: Boolean,
    @field:Size(max = 255, message = "O motivo não pode ultrapassar 255 caracteres.")
    val reason: String? = null
)

data class AdminAuditLogDto(
    val id: String,
    val adminUsername: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val details: String?,
    val clientIp: String?,
    val createdAt: Long
) {
    companion object {
        fun fromEntity(entity: AdminAuditLogEntity): AdminAuditLogDto {
            return AdminAuditLogDto(
                id = entity.id,
                adminUsername = entity.adminUsername,
                action = entity.action,
                targetType = entity.targetType,
                targetId = entity.targetId,
                details = entity.details,
                clientIp = entity.clientIp,
                createdAt = entity.createdAt
            )
        }
    }
}

typealias AdminGlobalProductItemDto = GlobalProductResponseDto
