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

package com.bitlabbr.minhadespensa.modules.admin.service

import com.bitlabbr.minhadespensa.common.exception.ResourceNotFoundException
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminBlockUserRequest
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminDashboardSummaryDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminPagedResponseDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminUserDetailDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminUserHouseholdDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminUserSummaryDto
import com.bitlabbr.minhadespensa.modules.auth.repository.RefreshTokenRepository
import com.bitlabbr.minhadespensa.modules.auth.repository.UserRepository
import com.bitlabbr.minhadespensa.modules.catalog.repository.GlobalProductRepository
import com.bitlabbr.minhadespensa.modules.household.repository.HouseholdMemberRepository
import com.bitlabbr.minhadespensa.modules.household.repository.HouseholdRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val householdMemberRepository: HouseholdMemberRepository,
    private val householdRepository: HouseholdRepository,
    private val globalProductRepository: GlobalProductRepository,
    private val adminAuditService: AdminAuditService
) {

    @Transactional(readOnly = true)
    fun getDashboardSummary(): AdminDashboardSummaryDto {
        return AdminDashboardSummaryDto(
            totalGlobalProducts = globalProductRepository.count(),
            verifiedGlobalProducts = globalProductRepository.countByIsVerifiedTrue(),
            productsWithPhoto = globalProductRepository.countByImageHashIsNotNull(),
            totalUsers = userRepository.count(),
            blockedUsers = userRepository.countByIsBlockedTrue(),
            totalHouseholds = householdRepository.count()
        )
    }

    @Transactional(readOnly = true)
    fun listUsers(
        query: String? = null,
        blocked: Boolean? = null,
        page: Int = 0,
        size: Int = 25
    ): AdminPagedResponseDto<AdminUserSummaryDto> {
        val cleanQuery = query?.trim()?.lowercase().orEmpty()
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        val resultPage = userRepository.adminFindFiltered(cleanQuery, blocked, pageable)

        val items = resultPage.content.map { user ->
            val memberships = householdMemberRepository.findByUserId(user.id)
            AdminUserSummaryDto(
                id = user.id,
                name = user.name,
                email = user.email,
                defaultHouseholdId = user.defaultHouseholdId,
                householdCount = memberships.size,
                isBlocked = user.isBlocked,
                blockedReason = user.blockedReason,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt
            )
        }

        return AdminPagedResponseDto(
            items = items,
            page = resultPage.number,
            size = resultPage.size,
            totalElements = resultPage.totalElements,
            totalPages = resultPage.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun getUserDetail(userId: String): AdminUserDetailDto {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("Usuário '$userId' não encontrado.", "User", userId) }

        val memberships = householdMemberRepository.findByUserId(user.id)
        val householdIds = memberships.map { it.householdId }
        val householdsById = if (householdIds.isNotEmpty()) {
            householdRepository.findAllById(householdIds).associateBy { it.id }
        } else {
            emptyMap()
        }

        val householdDtos = memberships.mapNotNull { member ->
            val house = householdsById[member.householdId] ?: return@mapNotNull null
            AdminUserHouseholdDto(
                householdId = house.id,
                householdName = house.name,
                role = member.role.name,
                inviteCode = house.inviteCode
            )
        }

        return AdminUserDetailDto(
            id = user.id,
            name = user.name,
            email = user.email,
            defaultHouseholdId = user.defaultHouseholdId,
            isBlocked = user.isBlocked,
            blockedReason = user.blockedReason,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
            households = householdDtos
        )
    }

    @Transactional
    fun setUserBlocked(
        userId: String,
        request: AdminBlockUserRequest,
        adminUsername: String,
        clientIp: String?
    ): AdminUserSummaryDto {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("Usuário '$userId' não encontrado.", "User", userId) }

        user.isBlocked = request.blocked
        user.blockedReason = if (request.blocked) {
            request.reason?.trim()?.takeIf { it.isNotEmpty() } ?: "Bloqueado pelo administrador"
        } else {
            null
        }
        user.updatedAt = System.currentTimeMillis()
        val saved = userRepository.save(user)

        if (request.blocked) {
            refreshTokenRepository.deleteByUserId(user.id)
        }

        adminAuditService.record(
            adminUsername = adminUsername,
            action = if (request.blocked) "USER_BLOCK" else "USER_UNBLOCK",
            targetType = "USER",
            targetId = user.id,
            details = "email=${user.email}, reason=${user.blockedReason ?: "-"}",
            clientIp = clientIp
        )

        val memberships = householdMemberRepository.findByUserId(saved.id)
        return AdminUserSummaryDto(
            id = saved.id,
            name = saved.name,
            email = saved.email,
            defaultHouseholdId = saved.defaultHouseholdId,
            householdCount = memberships.size,
            isBlocked = saved.isBlocked,
            blockedReason = saved.blockedReason,
            createdAt = saved.createdAt,
            updatedAt = saved.updatedAt
        )
    }

    @Transactional
    fun revokeUserSessions(
        userId: String,
        adminUsername: String,
        clientIp: String?
    ): Int {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("Usuário '$userId' não encontrado.", "User", userId) }

        val deletedCount = refreshTokenRepository.deleteByUserId(user.id)

        adminAuditService.record(
            adminUsername = adminUsername,
            action = "USER_REVOKE_SESSIONS",
            targetType = "USER",
            targetId = user.id,
            details = "email=${user.email}, revokedTokens=$deletedCount",
            clientIp = clientIp
        )

        return deletedCount
    }
}
