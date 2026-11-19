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

package com.bitlabbr.minhadespensa.modules.admin.security

import com.bitlabbr.minhadespensa.common.exception.TooManyRequestsException
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class LoginRateLimiter(
    private val maxAttempts: Int = 5,
    private val windowMillis: Long = 15 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class AttemptBucket(
        val failures: MutableList<Long> = mutableListOf(),
        var blockedUntil: Long = 0L
    )

    private val buckets = ConcurrentHashMap<String, AttemptBucket>()

    fun checkAllowed(key: String) {
        val now = clock()
        val bucket = buckets[key] ?: return
        synchronized(bucket) {
            if (bucket.blockedUntil > now) {
                val remainingSeconds = ((bucket.blockedUntil - now) / 1000L).coerceAtLeast(1L)
                throw TooManyRequestsException(
                    "Muitas tentativas de login inválidas. Aguarde ${remainingSeconds}s antes de tentar novamente."
                )
            }
        }
    }

    fun recordFailure(key: String) {
        val now = clock()
        val bucket = buckets.computeIfAbsent(key) { AttemptBucket() }
        synchronized(bucket) {
            bucket.failures.removeIf { now - it > windowMillis }
            bucket.failures.add(now)
            if (bucket.failures.size >= maxAttempts) {
                bucket.blockedUntil = now + windowMillis
            }
        }
    }

    fun reset(key: String) {
        buckets.remove(key)
    }
}
