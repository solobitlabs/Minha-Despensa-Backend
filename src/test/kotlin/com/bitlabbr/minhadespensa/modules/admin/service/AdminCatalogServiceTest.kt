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

import com.bitlabbr.minhadespensa.common.exception.ConflictException
import com.bitlabbr.minhadespensa.common.exception.InvalidRequestException
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminCreateGlobalProductRequest
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminUpdateGlobalProductRequest
import com.bitlabbr.minhadespensa.modules.catalog.client.ExternalCatalogProduct
import com.bitlabbr.minhadespensa.modules.catalog.client.OpenFactsCatalogClient
import com.bitlabbr.minhadespensa.modules.catalog.domain.MeasureUnit
import com.bitlabbr.minhadespensa.modules.catalog.entity.GlobalProductEntity
import com.bitlabbr.minhadespensa.modules.catalog.entity.GlobalProductMediaEntity
import com.bitlabbr.minhadespensa.modules.catalog.repository.GlobalProductMediaRepository
import com.bitlabbr.minhadespensa.modules.catalog.repository.GlobalProductRepository
import com.bitlabbr.minhadespensa.modules.catalog.service.GlobalProductService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class AdminCatalogServiceTest {

    private lateinit var globalProductRepository: GlobalProductRepository
    private lateinit var globalProductMediaRepository: GlobalProductMediaRepository
    private lateinit var globalProductService: GlobalProductService
    private lateinit var adminAuditService: AdminAuditService
    private lateinit var openFactsCatalogClient: OpenFactsCatalogClient
    private lateinit var adminCatalogService: AdminCatalogService

    @BeforeEach
    fun setUp() {
        globalProductRepository = mock()
        globalProductMediaRepository = mock()
        globalProductService = GlobalProductService(globalProductRepository)
        adminAuditService = mock()
        openFactsCatalogClient = mock()
        adminCatalogService = AdminCatalogService(
            globalProductRepository = globalProductRepository,
            globalProductMediaRepository = globalProductMediaRepository,
            globalProductService = globalProductService,
            adminAuditService = adminAuditService,
            openFactsCatalogClient = openFactsCatalogClient
        )
    }

    @Test
    fun `createProduct should persist new ADMIN_CURATED verified product and record audit`() {
        val request = AdminCreateGlobalProductRequest(
            ean = "7891000100103",
            name = "Leite Condensado Moça 395g",
            brand = "Nestlé",
            category = "Laticínios",
            measureUnit = MeasureUnit.GRAM,
            netWeight = 395.0,
            isVerified = true
        )
        whenever(globalProductRepository.existsByEan("7891000100103")).thenReturn(false)
        whenever(globalProductRepository.save(any<GlobalProductEntity>())).thenAnswer { it.arguments[0] }

        val result = adminCatalogService.createProduct(request, "admin", "127.0.0.1")

        assertEquals("7891000100103", result.ean)
        assertEquals("ADMIN_CURATED", result.source)
        assertTrue(result.isVerified)
        assertEquals("Nestlé", result.brand)
        verify(adminAuditService).record(eq("admin"), eq("GLOBAL_PRODUCT_CREATE"), eq("GLOBAL_PRODUCT"), eq("7891000100103"), any(), eq("127.0.0.1"))
    }

    @Test
    fun `createProduct should throw ConflictException when EAN already exists`() {
        val request = AdminCreateGlobalProductRequest(ean = "7891000100103", name = "Produto")
        whenever(globalProductRepository.existsByEan("7891000100103")).thenReturn(true)

        assertThrows<ConflictException> {
            adminCatalogService.createProduct(request, "admin", "127.0.0.1")
        }
    }

    @Test
    fun `updateProduct should sanitize fields, mark as ADMIN_CURATED and prevent auto-overwrite`() {
        val existing = GlobalProductEntity(
            id = "global-1",
            ean = "7891000100103",
            name = "LEIT COND MOCA LATA",
            normalizedName = "leit cond moca lata",
            source = "SEFAZ_INVOICE",
            isVerified = false
        )
        whenever(globalProductRepository.findByEan("7891000100103")).thenReturn(Optional.of(existing))
        whenever(globalProductRepository.save(any<GlobalProductEntity>())).thenAnswer { it.arguments[0] }

        val updated = adminCatalogService.updateProduct(
            ean = "7891000100103",
            request = AdminUpdateGlobalProductRequest(
                name = "Leite Condensado Moça",
                brand = "Nestlé",
                category = "Laticínios",
                measureUnit = MeasureUnit.GRAM,
                netWeight = 395.0,
                isVerified = true
            ),
            adminUsername = "admin",
            clientIp = "10.0.0.1"
        )

        assertEquals("Leite Condensado Moça", updated.name)
        assertEquals("ADMIN_CURATED", updated.source)
        assertTrue(updated.isVerified)

        // Simula nova ingestão automática da SEFAZ tentando sobrescrever
        val afterSefaz = globalProductService.upsertGlobalProduct(
            ean = "7891000100103",
            name = "LEIT COND MOCA LATA 395G SUPERMERCADO X",
            brand = null,
            measureUnit = MeasureUnit.UNIT,
            netWeight = 1.0,
            source = "SEFAZ_INVOICE"
        )
        assertEquals("Leite Condensado Moça", afterSefaz.name)
        assertEquals("ADMIN_CURATED", afterSefaz.source)
    }

    @Test
    fun `uploadMedia should validate magic bytes, save media, and update imageHash`() {
        val existing = GlobalProductEntity(
            id = "global-1",
            ean = "7891000100103",
            name = "Leite Moça"
        )
        whenever(globalProductRepository.findByEan("7891000100103")).thenReturn(Optional.of(existing))
        whenever(globalProductMediaRepository.save(any<GlobalProductMediaEntity>())).thenAnswer { it.arguments[0] }
        whenever(globalProductRepository.save(any<GlobalProductEntity>())).thenAnswer { it.arguments[0] }

        // Cabeçalho mágico JPEG válido (FF D8 FF)
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x01, 0x02)
        val res = adminCatalogService.uploadMedia("7891000100103", "image/jpeg", jpegBytes, "admin", "127.0.0.1")

        assertEquals("image/jpeg", res.contentType)
        assertEquals(6, res.sizeBytes)
        assertEquals(res.sha256Hash, existing.imageHash)

        // Bytes inválidos (ex: script texto) devem ser rejeitados
        val invalidBytes = "<script>alert(1)</script>".toByteArray()
        assertThrows<InvalidRequestException> {
            adminCatalogService.uploadMedia("7891000100103", "image/jpeg", invalidBytes, "admin", "127.0.0.1")
        }
    }

    @Test
    fun `enrichFromExternal should fetch OpenFacts and update product`() {
        val existing = GlobalProductEntity(
            id = "global-1",
            ean = "7891000100103",
            name = "PRODUTO ABREV",
            source = "SEFAZ_INVOICE"
        )
        whenever(globalProductRepository.findByEan("7891000100103")).thenReturn(Optional.of(existing))
        whenever(openFactsCatalogClient.lookupByEan("7891000100103")).thenReturn(
            ExternalCatalogProduct(
                ean = "7891000100103",
                name = "Produto Completo API",
                brand = "Marca API",
                measureUnit = MeasureUnit.GRAM,
                netWeight = 500.0,
                source = "OPEN_FOOD_FACTS"
            )
        )
        whenever(globalProductRepository.save(any<GlobalProductEntity>())).thenAnswer { it.arguments[0] }

        val enriched = adminCatalogService.enrichFromExternal("7891000100103", "admin", "127.0.0.1")

        assertEquals("Produto Completo API", enriched.name)
        assertEquals("Marca API", enriched.brand)
        assertEquals("OPEN_FOOD_FACTS", enriched.source)
    }

    @Test
    fun `deleteMedia should remove imageHash and delete media entity`() {
        val existing = GlobalProductEntity(id = "global-1", ean = "7891000100103", imageHash = "abc")
        whenever(globalProductRepository.findByEan("7891000100103")).thenReturn(Optional.of(existing))
        whenever(globalProductMediaRepository.existsById("7891000100103")).thenReturn(true)
        whenever(globalProductRepository.save(any<GlobalProductEntity>())).thenAnswer { it.arguments[0] }

        adminCatalogService.deleteMedia("7891000100103", "admin", "127.0.0.1")

        assertNull(existing.imageHash)
        verify(globalProductMediaRepository).deleteById("7891000100103")
    }
}
