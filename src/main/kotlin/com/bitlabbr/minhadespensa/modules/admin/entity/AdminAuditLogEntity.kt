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

package com.bitlabbr.minhadespensa.modules.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(
    name = "admin_audit_logs",
    indexes = [
        Index(name = "idx_admin_audit_logs_created_at", columnList = "created_at"),
        Index(name = "idx_admin_audit_logs_target", columnList = "target_type, target_id")
    ]
)
class AdminAuditLogEntity(
    @Id
    @Column(name = "id", length = 36, nullable = false)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "admin_username", length = 80, nullable = false)
    var adminUsername: String = "",

    @Column(name = "action", length = 60, nullable = false)
    var action: String = "",

    @Column(name = "target_type", length = 40, nullable = false)
    var targetType: String = "",

    @Column(name = "target_id", length = 64, nullable = false)
    var targetId: String = "",

    @Column(name = "details", columnDefinition = "TEXT", nullable = true)
    var details: String? = null,

    @Column(name = "client_ip", length = 64, nullable = true)
    var clientIp: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Long = System.currentTimeMillis()
)
