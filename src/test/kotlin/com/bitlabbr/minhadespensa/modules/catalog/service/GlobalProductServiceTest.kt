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

package com.bitlabbr.minhadespensa.modules.catalog.service

import com.bitlabbr.minhadespensa.common.exception.ResourceNotFoundException
import com.bitlabbr.minhadespensa.common.exception.ValidationException
import com.bitlabbr.minhadespensa.common.util.DeterministicIdUtils
import com.bitlabbr.minhadespensa.modules.catalog.client.ExternalCatalogProduct
import com.bitlabbr.minhadespensa.modules.catalog.client.OpenFactsCatalogClient
import com.bitlabbr.minhadespensa.modules.catalog.domain.MeasureUnit
import com.bitlabbr.minhadespensa.modules.catalog.entity.GlobalProductEntity
import com.bitlabbr.minhadespensa.modules.catalog.entity.ProductEntity
import com.bitlabbr.minhadespensa.modules.catalog.repository.GlobalProductRepository
import com.bitlabbr.minhadespensa.modules.catalog.repository.ProductRepository
import com.bitlabbr.minhadespensa.modules.household.domain.HouseholdRole
import com.bitlabbr.minhadespensa.modules.household.security.HouseholdContext
import com.bitlabbr.minhadespensa.modules.household.service.HouseholdService
import com.bitlabbr.minhadespensa.modules.invoice.dto.InvoiceItemResponseDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import java.util.Optional

class GlobalProductServiceTest {

    private lateinit var globalProductRepository: GlobalProductRepository
    private lateinit var openFactsCatalogClient: OpenFactsCatalogClient
    private lateinit var productRepository: ProductRepository
    private lateinit var householdService: HouseholdService
    private lateinit var globalProductService: GlobalProductService

    @BeforeEach
    fun setUp() {
        globalProductRepository = mock()
        openFactsCatalogClient = mock()
        productRepository = mock()
        householdService = mock()
        globalProductService = GlobalProductService(
            globalProductRepository = globalProductRepository,
            openFactsCatalogClient = openFactsCatalogClient,
            productRepository = productRepository,
            householdService = householdService
        )
    }

    @Test
    fun `getProductByEan should return canonical DTO when product exists in global_products`() {
        val entity = GlobalProductEntity(
            id = "global-1",
            ean = "7891000100103",
            name = "Leite Condensado Moça",
            normalizedName = "leite condensado moca",
            brand = "Nestlé",
            category = "Outros",
            measureUnit = MeasureUnit.GRAM,
            netWeight = 395.0,
            source = "OPEN_FOOD_FACTS",
            occurrenceCount = 5L
        )
        whenever(globalProductRepository.findByEan("7891000100103")).thenReturn(Optional.of(entity))

        val result = globalProductService.getProductByEan(" 7891000100103 ")

        assertEquals("global-1", result.id)
        assertEquals("7891000100103", result.ean)
        assertEquals("Leite Condensado Moça", result.name)
        assertEquals("Nestlé", result.brand)
        assertEquals("Outros", result.category)
        assertEquals(MeasureUnit.GRAM, result.measureUnit)
        assertEquals(395.0, result.netWeight)
        assertEquals(5L, result.occurrenceCount)
        verify(openFactsCatalogClient, never()).lookupByEan(any())
    }

    @Test
    fun `getProductByEan should query OpenFactsCatalogClient on cache miss, persist canonical product and return it`() {
        whenever(globalProductRepository.findByEan("7891000100103")).thenReturn(Optional.empty())
        whenever(openFactsCatalogClient.lookupByEan("7891000100103")).thenReturn(
            ExternalCatalogProduct(
                ean = "7891000100103",
                name = "Sabão A",
                brand = "Marca X",
                measureUnit = MeasureUnit.GRAM,
                netWeight = 500.0,
                source = "OPEN_PRODUCTS_FACTS"
            )
        )
        whenever(globalProductRepository.save(any<GlobalProductEntity>())).thenAnswer { it.arguments[0] }

        val result = globalProductService.getProductByEan("7891000100103")

        assertEquals("Sabão A", result.name)
        assertEquals("Marca X", result.brand)
        assertEquals("OPEN_PRODUCTS_FACTS", result.source)
        assertEquals(MeasureUnit.GRAM, result.measureUnit)
        assertEquals(500.0, result.netWeight)
        verify(openFactsCatalogClient).lookupByEan("7891000100103")
        verify(globalProductRepository).save(any<GlobalProductEntity>())
    }

    @Test
    fun `getProductByEan with X-Household-Id should return user customized version for owner house and canonical for another house`() {
        val ean = "7891000100123"
        val userAProduct = ProductEntity(
            householdId = "house-a",
            id = "prod-a",
            ean = ean,
            name = "Sabão AAAA",
            normalizedName = "sabao aaaa",
            category = "Limpeza",
            brand = "Marca X",
            measureUnit = MeasureUnit.GRAM,
            netWeight = 500.0
        )
        val canonicalEntity = GlobalProductEntity(
            id = DeterministicIdUtils.productIdForEan(ean),
            ean = ean,
            name = "Sabão A",
            normalizedName = "sabao a",
            brand = "Marca X",
            category = "Outros",
            measureUnit = MeasureUnit.GRAM,
            netWeight = 500.0,
            source = "OPEN_PRODUCTS_FACTS"
        )

        whenever(householdService.requireHouseholdAccess("user-a", "house-a"))
            .thenReturn(HouseholdContext("house-a", "user-a", HouseholdRole.OWNER))
        whenever(householdService.requireHouseholdAccess("user-b", "house-b"))
            .thenReturn(HouseholdContext("house-b", "user-b", HouseholdRole.OWNER))

        whenever(productRepository.findByHouseholdIdAndEanAndIsDeletedFalse("house-a", ean))
            .thenReturn(Optional.of(userAProduct))
        whenever(productRepository.findByHouseholdIdAndEanAndIsDeletedFalse("house-b", ean))
            .thenReturn(Optional.empty())
        whenever(globalProductRepository.findByEan(ean)).thenReturn(Optional.of(canonicalEntity))

        val resultForUserA = globalProductService.getProductByEan(ean, userId = "user-a", householdId = "house-a")
        val resultForUserB = globalProductService.getProductByEan(ean, userId = "user-b", householdId = "house-b")

        assertEquals("Sabão AAAA", resultForUserA.name)
        assertEquals("HOUSEHOLD_CATALOG", resultForUserA.source)
        assertEquals("Sabão A", resultForUserB.name)
        assertEquals("OPEN_PRODUCTS_FACTS", resultForUserB.source)
    }

    @Test
    fun `getProductByEan should throw ResourceNotFoundException when product does not exist locally or externally`() {
        whenever(globalProductRepository.findByEan("7891000100103")).thenReturn(Optional.empty())
        whenever(openFactsCatalogClient.lookupByEan("7891000100103")).thenReturn(null)

        assertThrows<ResourceNotFoundException> {
            globalProductService.getProductByEan("7891000100103")
        }
    }

    @Test
    fun `getProductByEan should throw ValidationException when EAN is alphanumeric, invalid length, or all zeros`() {
        assertThrows<ValidationException> {
            globalProductService.getProductByEan("ABC12345")
        }
        assertThrows<ValidationException> {
            globalProductService.getProductByEan("12345")
        }
        assertThrows<ValidationException> {
            globalProductService.getProductByEan("0000000000000")
        }
    }

    @Test
    fun `ingestFromInvoiceItem should ignore invalid EAN or in-store restricted scale barcode (prefix 20-29)`() {
        val invalidEanItem = InvoiceItemResponseDto(
            ean = "SEM GTIN",
            name = "Pão Francês",
            quantity = 0.5,
            measureUnit = "KG"
        )
        val scaleBarcodeItem = InvoiceItemResponseDto(
            ean = "2001234001592",
            name = "QUEIJO MUSSARELA FATIADO",
            quantity = 0.35,
            measureUnit = "KG"
        )

        assertNull(globalProductService.ingestFromInvoiceItem(invalidEanItem))
        assertNull(globalProductService.ingestFromInvoiceItem(scaleBarcodeItem))
        verify(globalProductRepository, never()).save(any())
    }

    @Test
    fun `ingestFromInvoiceItem should create new record mapping SEFAZ measureUnit and keeping netWeight 1_0`() {
        val item = InvoiceItemResponseDto(
            ean = "7891000100103",
            name = "ARROZ TIPO 1 CAMIL",
            quantity = 3.0,
            measureUnit = "KG"
        )
        whenever(globalProductRepository.findByEan("7891000100103")).thenReturn(Optional.empty())
        whenever(globalProductRepository.save(any<GlobalProductEntity>())).thenAnswer { it.arguments[0] }

        val saved = globalProductService.ingestFromInvoiceItem(item)

        assertNotNull(saved)
        assertEquals(DeterministicIdUtils.productIdForEan("7891000100103"), saved!!.id)
        assertEquals("7891000100103", saved.ean)
        assertEquals("ARROZ TIPO 1 CAMIL", saved.name)
        assertEquals("arroz tipo 1 camil", saved.normalizedName)
        assertEquals("Outros", saved.category)
        assertEquals(MeasureUnit.KILOGRAM, saved.measureUnit)
        assertEquals(1.0, saved.netWeight)
        assertEquals("SEFAZ_INVOICE", saved.source)
        assertEquals(1L, saved.occurrenceCount)
    }

    @Test
    fun `ingestFromInvoiceItem should increment occurrenceCount and update updatedAt when EAN already exists`() {
        val existing = GlobalProductEntity(
            id = "global-1",
            ean = "7891000100103",
            name = "LEIT COND MOCA",
            normalizedName = "leit cond moca",
            source = "SEFAZ_INVOICE",
            occurrenceCount = 2L,
            updatedAt = 1000L
        )
        val item = InvoiceItemResponseDto(
            ean = "7891000100103",
            name = "LEIT COND MOCA 395G",
            quantity = 2.0,
            measureUnit = "UN"
        )
        whenever(globalProductRepository.findByEan("7891000100103")).thenReturn(Optional.of(existing))
        whenever(globalProductRepository.save(any<GlobalProductEntity>())).thenAnswer { it.arguments[0] }

        val saved = globalProductService.ingestFromInvoiceItem(item)

        assertNotNull(saved)
        assertEquals(3L, saved!!.occurrenceCount)
        assertEquals("LEIT COND MOCA 395G", saved.name)
        assertTrue(saved.updatedAt > 1000L)
    }

    @Test
    fun `ingestFromUserProduct should NOT overwrite canonical name from OPEN_FOOD_FACTS or SEFAZ_INVOICE`() {
        val existingCanonical = GlobalProductEntity(
            id = DeterministicIdUtils.productIdForEan("7891000100103"),
            ean = "7891000100103",
            name = "Sabão A",
            normalizedName = "sabao a",
            brand = null,
            category = "Outros",
            measureUnit = MeasureUnit.GRAM,
            netWeight = 500.0,
            source = "OPEN_PRODUCTS_FACTS",
            occurrenceCount = 4L
        )
        whenever(globalProductRepository.findByEan("7891000100103")).thenReturn(Optional.of(existingCanonical))
        whenever(globalProductRepository.save(any<GlobalProductEntity>())).thenAnswer { it.arguments[0] }

        val saved = globalProductService.ingestFromUserProduct(
            ean = "7891000100103",
            name = "Sabão AAAA",
            brand = "Marca X",
            category = "Limpeza",
            measureUnit = MeasureUnit.UNIT,
            netWeight = 1.0
        )

        assertNotNull(saved)
        assertEquals("Sabão A", saved!!.name)
        assertEquals("sabao a", saved.normalizedName)
        assertEquals("OPEN_PRODUCTS_FACTS", saved.source)
        assertEquals("Marca X", saved.brand)
        assertEquals(MeasureUnit.GRAM, saved.measureUnit)
        assertEquals(500.0, saved.netWeight)
        assertEquals(5L, saved.occurrenceCount)
    }

    @Test
    fun `ingestFromExternalCatalog should promote and overwrite initial USER_SYNC entry with canonical data`() {
        val existingUserInitial = GlobalProductEntity(
            id = DeterministicIdUtils.productIdForEan("7891000100103"),
            ean = "7891000100103",
            name = "Sabão AAAA",
            normalizedName = "sabao aaaa",
            brand = null,
            category = "Outros",
            measureUnit = MeasureUnit.UNIT,
            netWeight = 1.0,
            source = "USER_SYNC",
            occurrenceCount = 1L
        )
        whenever(globalProductRepository.findByEan("7891000100103")).thenReturn(Optional.of(existingUserInitial))
        whenever(globalProductRepository.save(any<GlobalProductEntity>())).thenAnswer { it.arguments[0] }

        val saved = globalProductService.ingestFromExternalCatalog(
            ExternalCatalogProduct(
                ean = "7891000100103",
                name = "Sabão A",
                brand = "Marca Oficial",
                measureUnit = MeasureUnit.GRAM,
                netWeight = 500.0,
                source = "OPEN_PRODUCTS_FACTS"
            )
        )

        assertEquals("Sabão A", saved.name)
        assertEquals("sabao a", saved.normalizedName)
        assertEquals("Marca Oficial", saved.brand)
        assertEquals(MeasureUnit.GRAM, saved.measureUnit)
        assertEquals(500.0, saved.netWeight)
        assertEquals("OPEN_PRODUCTS_FACTS", saved.source)
        assertEquals(2L, saved.occurrenceCount)
    }

    @Test
    fun `searchProducts should return empty list for short query and merge household overrides before canonical results`() {
        assertTrue(globalProductService.searchProducts("a", 20).isEmpty())
        verify(globalProductRepository, never()).searchProducts(any(), any(), any())

        val householdOverride = ProductEntity(
            householdId = "house-a",
            id = "prod-a",
            ean = "7891000100103",
            name = "Sabão AAAA",
            category = "Limpeza",
            brand = "Marca X"
        )
        val canonicalSameEan = GlobalProductEntity(
            id = "global-1",
            ean = "7891000100103",
            name = "Sabão A",
            normalizedName = "sabao a",
            brand = "Marca X",
            source = "OPEN_PRODUCTS_FACTS"
        )
        val canonicalOtherEan = GlobalProductEntity(
            id = "global-2",
            ean = "7891000100999",
            name = "Sabão B",
            normalizedName = "sabao b",
            brand = "Marca Y",
            source = "OPEN_PRODUCTS_FACTS"
        )

        whenever(householdService.requireHouseholdAccess("user-a", "house-a"))
            .thenReturn(HouseholdContext("house-a", "user-a", HouseholdRole.OWNER))
        whenever(productRepository.searchProducts("house-a", "Sabão"))
            .thenReturn(listOf(householdOverride))
        whenever(globalProductRepository.searchProducts(eq("sabao"), eq("sabão"), eq(PageRequest.of(0, 20))))
            .thenReturn(listOf(canonicalSameEan, canonicalOtherEan))

        val results = globalProductService.searchProducts("Sabão", 20, userId = "user-a", householdId = "house-a")

        assertEquals(2, results.size)
        assertEquals("Sabão AAAA", results[0].name)
        assertEquals("HOUSEHOLD_CATALOG", results[0].source)
        assertEquals("Sabão B", results[1].name)
        assertEquals("OPEN_PRODUCTS_FACTS", results[1].source)
    }
}
