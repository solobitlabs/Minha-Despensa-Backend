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

import com.bitlabbr.minhadespensa.modules.admin.dto.AdminBlockUserRequest
import com.bitlabbr.minhadespensa.modules.auth.entity.UserEntity
import com.bitlabbr.minhadespensa.modules.auth.repository.RefreshTokenRepository
import com.bitlabbr.minhadespensa.modules.auth.repository.UserRepository
import com.bitlabbr.minhadespensa.modules.catalog.repository.GlobalProductRepository
import com.bitlabbr.minhadespensa.modules.household.repository.HouseholdMemberRepository
import com.bitlabbr.minhadespensa.modules.household.repository.HouseholdRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class AdminUserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var householdMemberRepository: HouseholdMemberRepository
    private lateinit var householdRepository: HouseholdRepository
    private lateinit var globalProductRepository: GlobalProductRepository
    private lateinit var adminAuditService: AdminAuditService
    private lateinit var adminUserService: AdminUserService

    @BeforeEach
    fun setUp() {
        userRepository = mock()
        refreshTokenRepository = mock()
        householdMemberRepository = mock()
        householdRepository = mock()
        globalProductRepository = mock()
        adminAuditService = mock()
        adminUserService = AdminUserService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            householdMemberRepository = householdMemberRepository,
            householdRepository = householdRepository,
            globalProductRepository = globalProductRepository,
            adminAuditService = adminAuditService
        )
    }

    @Test
    fun `setUserBlocked should block user, revoke refresh tokens and record audit`() {
        val user = UserEntity(id = "user-1", name = "João", email = "joao@test.com", passwordHash = "hash")
        whenever(userRepository.findById("user-1")).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }
        whenever(householdMemberRepository.findByUserId("user-1")).thenReturn(emptyList())

        val result = adminUserService.setUserBlocked(
            userId = "user-1",
            request = AdminBlockUserRequest(blocked = true, reason = "Abuso de API"),
            adminUsername = "admin",
            clientIp = "127.0.0.1"
        )

        assertTrue(result.isBlocked)
        assertEquals("Abuso de API", result.blockedReason)
        verify(refreshTokenRepository).deleteByUserId("user-1")
        verify(adminAuditService).record(eq("admin"), eq("USER_BLOCK"), eq("USER"), eq("user-1"), any(), eq("127.0.0.1"))
    }

    @Test
    fun `setUserBlocked false should unblock user and clear blockedReason`() {
        val user = UserEntity(
            id = "user-1",
            name = "João",
            email = "joao@test.com",
            passwordHash = "hash",
            isBlocked = true,
            blockedReason = "Motivo antigo"
        )
        whenever(userRepository.findById("user-1")).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }
        whenever(householdMemberRepository.findByUserId("user-1")).thenReturn(emptyList())

        val result = adminUserService.setUserBlocked(
            userId = "user-1",
            request = AdminBlockUserRequest(blocked = false),
            adminUsername = "admin",
            clientIp = "127.0.0.1"
        )

        assertFalse(result.isBlocked)
        assertNull(result.blockedReason)
    }

    @Test
    fun `getDashboardSummary should aggregate counts across repositories`() {
        whenever(globalProductRepository.count()).thenReturn(100L)
        whenever(globalProductRepository.countByIsVerifiedTrue()).thenReturn(40L)
        whenever(globalProductRepository.countByImageHashIsNotNull()).thenReturn(15L)
        whenever(userRepository.count()).thenReturn(20L)
        whenever(userRepository.countByIsBlockedTrue()).thenReturn(2L)
        whenever(householdRepository.count()).thenReturn(12L)

        val summary = adminUserService.getDashboardSummary()

        assertEquals(100L, summary.totalGlobalProducts)
        assertEquals(40L, summary.verifiedGlobalProducts)
        assertEquals(15L, summary.productsWithPhoto)
        assertEquals(20L, summary.totalUsers)
        assertEquals(2L, summary.blockedUsers)
        assertEquals(12L, summary.totalHouseholds)
    }
}
