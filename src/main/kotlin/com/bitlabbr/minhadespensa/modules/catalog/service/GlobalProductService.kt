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
import com.bitlabbr.minhadespensa.modules.catalog.dto.GlobalProductResponseDto
import com.bitlabbr.minhadespensa.modules.catalog.entity.GlobalProductEntity
import com.bitlabbr.minhadespensa.modules.catalog.entity.GlobalProductMediaEntity
import com.bitlabbr.minhadespensa.modules.catalog.entity.ProductEntity
import com.bitlabbr.minhadespensa.modules.catalog.repository.GlobalProductMediaRepository
import com.bitlabbr.minhadespensa.modules.catalog.repository.GlobalProductRepository
import com.bitlabbr.minhadespensa.modules.catalog.repository.ProductRepository
import com.bitlabbr.minhadespensa.modules.household.service.HouseholdService
import com.bitlabbr.minhadespensa.modules.invoice.dto.InvoiceItemResponseDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class GlobalProductService(
    private val globalProductRepository: GlobalProductRepository,
    @Autowired(required = false)
    private val openFactsCatalogClient: OpenFactsCatalogClient? = null,
    @Autowired(required = false)
    private val productRepository: ProductRepository? = null,
    @Autowired(required = false)
    private val householdService: HouseholdService? = null,
    @Autowired(required = false)
    private val globalProductMediaRepository: GlobalProductMediaRepository? = null
) {
    private val logger = LoggerFactory.getLogger(GlobalProductService::class.java)

    companion object {
        const val FIXED_GLOBAL_CATEGORY = "Outros"
        const val SOURCE_ADMIN_CURATED = "ADMIN_CURATED"
        const val SOURCE_USER_SYNC = "USER_SYNC"
        const val SOURCE_SEFAZ_INVOICE = "SEFAZ_INVOICE"
        const val SOURCE_HOUSEHOLD_CATALOG = "HOUSEHOLD_CATALOG"
        private val VALID_EAN_LENGTHS = setOf(8, 12, 13, 14)
        private val IN_STORE_RESTRICTED_PREFIXES = (20..29).map { it.toString() }.toSet()
    }

    fun isValidEan(ean: String?): Boolean {
        if (ean.isNullOrBlank()) return false
        val clean = ean.trim()
        return clean.all { it.isDigit() } &&
            clean.length in VALID_EAN_LENGTHS &&
            !clean.all { it == '0' }
    }

    fun isIngestibleGlobalEan(ean: String?): Boolean {
        if (!isValidEan(ean)) return false
        val clean = ean!!.trim()
        // Ignora EAN-13 de circulação restrita interna de supermercado (etiquetas de balança/peso variável prefixo 20..29)
        if (clean.length == 13 && IN_STORE_RESTRICTED_PREFIXES.any { clean.startsWith(it) }) {
            return false
        }
        return true
    }

    internal fun mapInvoiceMeasureUnit(rawUnit: String?): MeasureUnit {
        val normalized = rawUnit?.trim()?.uppercase() ?: return MeasureUnit.UNIT
        return when (normalized) {
            "KG", "KILO", "QUILO", "KILOGRAM", "KILOGRAMA" -> MeasureUnit.KILOGRAM
            "G", "GR", "GRAM", "GRAMA", "GRAMAS" -> MeasureUnit.GRAM
            "L", "LT", "LITRO", "LITROS", "LITER" -> MeasureUnit.LITER
            "ML", "MILILITRO", "MILLILITER" -> MeasureUnit.MILLILITER
            "PCT", "PC", "PACOTE", "CX", "CAIXA", "FD", "FARDO", "PACKAGE", "EMB" -> MeasureUnit.PACKAGE
            "UN", "UND", "UNID", "UNIDADE", "UNIT" -> MeasureUnit.UNIT
            else -> runCatching { MeasureUnit.valueOf(normalized) }.getOrDefault(MeasureUnit.UNIT)
        }
    }

    private fun sourcePriority(source: String): Int {
        return when {
            source == SOURCE_ADMIN_CURATED -> 4
            source.startsWith("OPEN_") -> 3
            source == SOURCE_SEFAZ_INVOICE -> 2
            else -> 1
        }
    }

    @Transactional(readOnly = true)
    fun getGlobalMedia(ean: String): GlobalProductMediaEntity {
        val cleanEan = ean.trim()
        if (!isValidEan(cleanEan)) {
            throw ValidationException("Código EAN inválido: '$ean'.")
        }
        val repo = globalProductMediaRepository
            ?: throw ResourceNotFoundException("Repositório de mídia global indisponível.")
        return repo.findById(cleanEan)
            .orElseThrow { ResourceNotFoundException("Foto canônica não encontrada para o EAN '$cleanEan'.") }
    }

    @Transactional
    fun getProductByEan(
        ean: String,
        userId: String? = null,
        householdId: String? = null
    ): GlobalProductResponseDto {
        val cleanEan = ean.trim()
        if (!isValidEan(cleanEan)) {
            logger.warn("[Catalog:Global] Invalid EAN format requested: '{}'", ean)
            throw ValidationException("Código EAN inválido: '$ean'. Deve conter 8, 12, 13 ou 14 dígitos numéricos válidos.")
        }

        // 1. Se o cliente informou X-Household-Id, prioriza a versão customizada da casa
        val cleanHouseholdId = householdId?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanHouseholdId != null && productRepository != null) {
            if (userId != null && householdService != null) {
                householdService.requireHouseholdAccess(userId, cleanHouseholdId)
            }
            val householdProductOpt = productRepository.findByHouseholdIdAndEanAndIsDeletedFalse(cleanHouseholdId, cleanEan)
            if (householdProductOpt.isPresent) {
                val hp = householdProductOpt.get()
                logger.debug(
                    "[Catalog:Global] Household override hit for EAN={} in household={} ('{}')",
                    cleanEan,
                    cleanHouseholdId,
                    hp.name
                )
                return fromHouseholdProduct(hp)
            }
        }

        // 2. Consulta o cache canônico local (global_products)
        val existingOpt = globalProductRepository.findByEan(cleanEan)
        if (existingOpt.isPresent) {
            val entity = existingOpt.get()
            logger.debug("[Catalog:Global] Lookup hit for EAN={} ('{}', occurrences={})", cleanEan, entity.name, entity.occurrenceCount)
            return GlobalProductResponseDto.fromEntity(entity)
        }

        // 3. Fallback externo em cascata (Open Food / Beauty / Products Facts) com cache write-through
        if (openFactsCatalogClient != null && isIngestibleGlobalEan(cleanEan)) {
            val external = openFactsCatalogClient.lookupByEan(cleanEan)
            if (external != null) {
                val persisted = ingestFromExternalCatalog(external)
                return GlobalProductResponseDto.fromEntity(persisted)
            }
        }

        logger.debug("[Catalog:Global] Product not found for EAN={}", cleanEan)
        throw ResourceNotFoundException("Produto global com EAN '$cleanEan' não encontrado no catálogo centralizado.")
    }

    @Transactional(readOnly = true)
    fun searchProducts(
        query: String,
        limit: Int = 20,
        userId: String? = null,
        householdId: String? = null
    ): List<GlobalProductResponseDto> {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            return emptyList()
        }
        val effectiveLimit = limit.coerceIn(1, 50)

        val householdResults = mutableListOf<GlobalProductResponseDto>()
        val householdEans = mutableSetOf<String>()
        val cleanHouseholdId = householdId?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanHouseholdId != null && productRepository != null) {
            if (userId != null && householdService != null) {
                householdService.requireHouseholdAccess(userId, cleanHouseholdId)
            }
            val localProducts = productRepository.searchProducts(cleanHouseholdId, trimmed)
            for (hp in localProducts) {
                householdResults.add(fromHouseholdProduct(hp))
                hp.ean?.trim()?.takeIf { it.isNotEmpty() }?.let { householdEans.add(it) }
                if (householdResults.size >= effectiveLimit) {
                    break
                }
            }
        }

        val remaining = effectiveLimit - householdResults.size
        if (remaining <= 0) {
            return householdResults
        }

        val pageable = PageRequest.of(0, effectiveLimit)
        val normalized = DeterministicIdUtils.normalizeText(trimmed)
        val rawLower = trimmed.lowercase()
        val globalResults = globalProductRepository.searchProducts(normalized, rawLower, pageable)
            .filter { it.ean !in householdEans }
            .take(remaining)
            .map { GlobalProductResponseDto.fromEntity(it) }

        val merged = householdResults + globalResults
        logger.debug(
            "[Catalog:Global] Search query='{}' (limit={}, householdId={}) returned {} results ({} household, {} canonical)",
            trimmed,
            effectiveLimit,
            cleanHouseholdId,
            merged.size,
            householdResults.size,
            globalResults.size
        )
        return merged
    }

    internal fun ingestFromExternalCatalog(external: ExternalCatalogProduct): GlobalProductEntity {
        return upsertGlobalProduct(
            ean = external.ean,
            name = external.name,
            brand = external.brand,
            measureUnit = external.measureUnit,
            netWeight = external.netWeight,
            source = external.source
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun ingestFromInvoiceItem(item: InvoiceItemResponseDto): GlobalProductEntity? {
        val rawEan = item.ean?.trim()
        if (!isIngestibleGlobalEan(rawEan)) {
            return null
        }
        val cleanEan = rawEan!!
        val name = item.name.trim()
        if (name.isBlank()) return null

        val unit = mapInvoiceMeasureUnit(item.measureUnit)

        return upsertGlobalProduct(
            ean = cleanEan,
            name = name,
            brand = null,
            measureUnit = unit,
            netWeight = 1.0,
            source = SOURCE_SEFAZ_INVOICE
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun ingestFromUserProduct(
        ean: String?,
        name: String,
        brand: String?,
        category: String? = null,
        measureUnit: MeasureUnit,
        netWeight: Double
    ): GlobalProductEntity? {
        val cleanEan = ean?.trim()
        if (!isIngestibleGlobalEan(cleanEan)) return null
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null

        return upsertGlobalProduct(
            ean = cleanEan!!,
            name = cleanName,
            brand = brand?.trim()?.takeIf { it.isNotEmpty() },
            measureUnit = measureUnit,
            netWeight = if (netWeight > 0.0) netWeight else 1.0,
            source = SOURCE_USER_SYNC
        )
    }

    @Transactional
    internal fun upsertGlobalProduct(
        ean: String,
        name: String,
        brand: String?,
        measureUnit: MeasureUnit,
        netWeight: Double,
        source: String
    ): GlobalProductEntity {
        val existingOpt = globalProductRepository.findByEan(ean)
        val now = System.currentTimeMillis()
        val deterministicId = DeterministicIdUtils.productIdForEan(ean)

        return if (existingOpt.isPresent) {
            val existing = existingOpt.get()
            existing.occurrenceCount += 1
            existing.updatedAt = now
            existing.category = FIXED_GLOBAL_CATEGORY

            val incomingPriority = sourcePriority(source)
            val existingPriority = sourcePriority(existing.source)

            // Produtos curados/verificados por administrador nunca são sobrescritos por ingestão automática
            if (existing.isVerified || existing.source == SOURCE_ADMIN_CURATED) {
                logger.debug(
                    "[Catalog:Global] Skipping auto-enrichment on admin-verified product EAN={} (occurrences={})",
                    ean,
                    existing.occurrenceCount
                )
                return globalProductRepository.save(existing)
            }

            if (existing.brand.isNullOrBlank() && !brand.isNullOrBlank()) {
                existing.brand = brand
            } else if (incomingPriority > existingPriority && !brand.isNullOrBlank()) {
                existing.brand = brand
            }

            // Proteção do Catálogo Canônico:
            // - Fontes canônicas superiores (OPEN_* > SEFAZ_INVOICE > USER_SYNC) sempre promovem e atualizam o registro.
            // - Fontes canônicas de mesma prioridade (OPEN_* ou SEFAZ_INVOICE) podem enriquecer nomes mais completos.
            // - USER_SYNC nunca sobrescreve o nome/unidade de um registro já existente (edições individuais ficam na casa).
            if (incomingPriority > existingPriority) {
                existing.name = name
                existing.normalizedName = DeterministicIdUtils.normalizeText(name)
                existing.source = source
            } else if (
                incomingPriority == existingPriority &&
                source != SOURCE_USER_SYNC &&
                name.length > existing.name.length &&
                !name.contains("*")
            ) {
                existing.name = name
                existing.normalizedName = DeterministicIdUtils.normalizeText(name)
            }

            if (incomingPriority > existingPriority) {
                if (measureUnit != MeasureUnit.UNIT) {
                    existing.measureUnit = measureUnit
                }
                if (netWeight > 0.0 && (measureUnit != MeasureUnit.UNIT || netWeight != 1.0)) {
                    existing.netWeight = netWeight
                }
            } else if (source != SOURCE_USER_SYNC) {
                if (existing.measureUnit == MeasureUnit.UNIT && measureUnit != MeasureUnit.UNIT) {
                    existing.measureUnit = measureUnit
                    existing.netWeight = netWeight
                } else if (existing.measureUnit == measureUnit && existing.netWeight == 1.0 && netWeight > 1.0) {
                    existing.netWeight = netWeight
                }
            }

            logger.info(
                "[Catalog:Global] Enriched global product EAN={} (id={}, '{}', source={}, occurrences={}) from incomingSource={}",
                ean,
                existing.id,
                existing.name,
                existing.source,
                existing.occurrenceCount,
                source
            )
            globalProductRepository.save(existing)
        } else {
            val entity = GlobalProductEntity(
                id = deterministicId,
                ean = ean,
                name = name,
                normalizedName = DeterministicIdUtils.normalizeText(name),
                brand = brand,
                category = FIXED_GLOBAL_CATEGORY,
                measureUnit = measureUnit,
                netWeight = netWeight,
                source = source,
                occurrenceCount = 1L,
                createdAt = now,
                updatedAt = now
            )
            val saved = globalProductRepository.save(entity)
            logger.info("[Catalog:Global] Ingested new global product EAN={} (id={}, '{}') from source={}", ean, saved.id, name, source)
            saved
        }
    }

    private fun fromHouseholdProduct(product: ProductEntity): GlobalProductResponseDto {
        return GlobalProductResponseDto(
            id = product.id,
            ean = product.ean ?: "",
            name = product.name,
            brand = product.brand,
            category = product.category,
            measureUnit = product.measureUnit,
            netWeight = product.netWeight,
            source = SOURCE_HOUSEHOLD_CATALOG,
            occurrenceCount = 1L,
            updatedAt = product.updatedAt
        )
    }
}
