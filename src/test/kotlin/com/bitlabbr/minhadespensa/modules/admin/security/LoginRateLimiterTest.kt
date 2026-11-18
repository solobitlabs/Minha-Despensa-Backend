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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class LoginRateLimiterTest {

    @Test
    fun `should block after maxAttempts failures and unblock after window expires`() {
        var now = 1_000_000L
        val limiter = LoginRateLimiter(maxAttempts = 3, windowMillis = 60_000L) { now }
        val key = "127.0.0.1:admin"

        assertDoesNotThrow { limiter.checkAllowed(key) }
        limiter.recordFailure(key)
        limiter.recordFailure(key)
        assertDoesNotThrow { limiter.checkAllowed(key) }

        limiter.recordFailure(key)
        assertThrows<TooManyRequestsException> { limiter.checkAllowed(key) }

        now += 61_000L
        assertDoesNotThrow { limiter.checkAllowed(key) }
    }

    @Test
    fun `should reset bucket on successful login`() {
        val limiter = LoginRateLimiter(maxAttempts = 2, windowMillis = 60_000L) { 1000L }
        val key = "10.0.0.1:admin"

        limiter.recordFailure(key)
        limiter.reset(key)
        limiter.recordFailure(key)
        assertDoesNotThrow { limiter.checkAllowed(key) }
    }
}
