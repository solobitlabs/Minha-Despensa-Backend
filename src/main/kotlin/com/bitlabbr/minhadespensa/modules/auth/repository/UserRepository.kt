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

package com.bitlabbr.minhadespensa.modules.auth.repository

import com.bitlabbr.minhadespensa.modules.auth.entity.UserEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<UserEntity, String> {
    fun findByEmail(email: String): Optional<UserEntity>
    fun existsByEmail(email: String): Boolean
    fun countByIsBlockedTrue(): Long

    @Query(
        """
        SELECT u FROM UserEntity u
        WHERE (:query = '' OR LOWER(u.name) LIKE CONCAT('%', :query, '%')
               OR LOWER(u.email) LIKE CONCAT('%', :query, '%')
               OR u.id = :query)
          AND (:blocked IS NULL OR u.isBlocked = :blocked)
        ORDER BY u.createdAt DESC
        """
    )
    fun adminFindFiltered(
        @Param("query") query: String,
        @Param("blocked") blocked: Boolean?,
        pageable: Pageable
    ): Page<UserEntity>
}
