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

import com.bitlabbr.minhadespensa.modules.admin.dto.AdminAuditLogDto
import com.bitlabbr.minhadespensa.modules.admin.entity.AdminAuditLogEntity
import com.bitlabbr.minhadespensa.modules.admin.repository.AdminAuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminAuditService(
    private val auditLogRepository: AdminAuditLogRepository
) {
    private val logger = LoggerFactory.getLogger(AdminAuditService::class.java)

    @Transactional
    fun record(
        adminUsername: String,
        action: String,
        targetType: String,
        targetId: String,
        details: String? = null,
        clientIp: String? = null
    ) {
        val entry = AdminAuditLogEntity(
            adminUsername = adminUsername,
            action = action,
            targetType = targetType,
            targetId = targetId,
            details = details,
            clientIp = clientIp
        )
        auditLogRepository.save(entry)
        logger.info(
            "[Admin:Audit] admin='{}' action={} target={}:{} ip={} details={}",
            adminUsername,
            action,
            targetType,
            targetId,
            clientIp ?: "unknown",
            details ?: "-"
        )
    }

    @Transactional(readOnly = true)
    fun listRecent(limit: Int = 50): List<AdminAuditLogDto> {
        val pageable = PageRequest.of(0, limit.coerceIn(1, 200))
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
            .map { AdminAuditLogDto.fromEntity(it) }
    }
}
