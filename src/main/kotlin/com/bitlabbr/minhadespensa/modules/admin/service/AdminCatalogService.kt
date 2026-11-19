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
import com.bitlabbr.minhadespensa.common.exception.ResourceNotFoundException
import com.bitlabbr.minhadespensa.common.util.DeterministicIdUtils
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminCreateGlobalProductRequest
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminPagedResponseDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminProductMediaUploadResponseDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminUpdateGlobalProductRequest
import com.bitlabbr.minhadespensa.modules.catalog.client.OpenFactsCatalogClient
import com.bitlabbr.minhadespensa.modules.catalog.dto.GlobalProductResponseDto
import com.bitlabbr.minhadespensa.modules.catalog.entity.GlobalProductEntity
import com.bitlabbr.minhadespensa.modules.catalog.entity.GlobalProductMediaEntity
import com.bitlabbr.minhadespensa.modules.catalog.repository.GlobalProductMediaRepository
import com.bitlabbr.minhadespensa.modules.catalog.repository.GlobalProductRepository
import com.bitlabbr.minhadespensa.modules.catalog.repository.ProductMediaRepository
import com.bitlabbr.minhadespensa.modules.catalog.service.GlobalProductService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

@Service
class AdminCatalogService(
    private val globalProductRepository: GlobalProductRepository,
    private val globalProductMediaRepository: GlobalProductMediaRepository,
    private val globalProductService: GlobalProductService,
    private val adminAuditService: AdminAuditService,
    @Autowired(required = false)
    private val openFactsCatalogClient: OpenFactsCatalogClient? = null,
    @Autowired(required = false)
    private val productMediaRepository: ProductMediaRepository? = null
) {
    private val logger = LoggerFactory.getLogger(AdminCatalogService::class.java)

    companion object {
        const val MAX_MEDIA_BYTES = 500 * 1024 // 500 KB
        private val ALLOWED_CONTENT_TYPES = setOf("image/webp", "image/jpeg", "image/png")
    }

    @Transactional(readOnly = true)
    fun listProducts(
        query: String? = null,
        source: String? = null,
        verified: Boolean? = null,
        page: Int = 0,
        size: Int = 25
    ): AdminPagedResponseDto<GlobalProductResponseDto> {
        val raw = query?.trim().orEmpty()
        val normalized = if (raw.isNotEmpty()) DeterministicIdUtils.normalizeText(raw) else ""
        val cleanSource = source?.trim().orEmpty()
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))

        val resultPage = globalProductRepository.adminFindFiltered(
            query = normalized,
            rawQuery = raw.lowercase(),
            source = cleanSource,
            verified = verified,
            pageable = pageable
        )

        val missingHashEans = resultPage.content
            .filter { it.imageHash == null }
            .map { it.ean }

        val fallbackHashes = if (missingHashEans.isNotEmpty() && productMediaRepository != null) {
            productMediaRepository.findMediaHashesByEans(missingHashEans)
                .mapNotNull { row ->
                    val ean = row.getOrNull(0) as? String
                    val hash = row.getOrNull(1) as? String
                    if (ean != null && hash != null) ean to hash else null
                }
                .toMap()
        } else {
            emptyMap()
        }

        val items = resultPage.content.map { entity ->
            val dto = GlobalProductResponseDto.fromEntity(entity)
            if (dto.imageHash == null && fallbackHashes.containsKey(dto.ean)) {
                dto.copy(imageHash = fallbackHashes[dto.ean])
            } else {
                dto
            }
        }

        return AdminPagedResponseDto(
            items = items,
            page = resultPage.number,
            size = resultPage.size,
            totalElements = resultPage.totalElements,
            totalPages = resultPage.totalPages
        )
    }

    data class AdminMediaPreview(
        val contentType: String,
        val sha256Hash: String,
        val imageBytes: ByteArray,
        val origin: String
    )

    @Transactional(readOnly = true)
    fun getMediaPreview(ean: String): AdminMediaPreview {
        val cleanEan = ean.trim()
        val canonicalOpt = globalProductMediaRepository.findById(cleanEan)
        if (canonicalOpt.isPresent) {
            val m = canonicalOpt.get()
            return AdminMediaPreview(m.contentType, m.sha256Hash, m.imageBytes, "CANONICAL")
        }

        if (productMediaRepository != null) {
            val householdMedia = productMediaRepository.findLatestMediaByEan(cleanEan, PageRequest.of(0, 1))
            if (householdMedia.isNotEmpty()) {
                val m = householdMedia.first()
                return AdminMediaPreview(m.contentType, m.sha256Hash, m.imageBytes, "HOUSEHOLD_FALLBACK")
            }
        }

        throw ResourceNotFoundException("Nenhuma foto encontrada para o EAN '$cleanEan'.")
    }

    @Transactional
    fun promoteHouseholdMediaToCanonical(
        ean: String,
        adminUsername: String,
        clientIp: String?
    ): AdminProductMediaUploadResponseDto {
        val cleanEan = ean.trim()
        val repo = productMediaRepository
            ?: throw ResourceNotFoundException("Repositório de mídia das casas indisponível.")
        val householdMedia = repo.findLatestMediaByEan(cleanEan, PageRequest.of(0, 1)).firstOrNull()
            ?: throw ResourceNotFoundException("Nenhuma foto de usuário encontrada para o EAN '$cleanEan'.")

        return uploadMedia(
            ean = cleanEan,
            contentType = householdMedia.contentType,
            bytes = householdMedia.imageBytes,
            adminUsername = adminUsername,
            clientIp = clientIp
        )
    }

    @Transactional
    fun createProduct(
        request: AdminCreateGlobalProductRequest,
        adminUsername: String,
        clientIp: String?
    ): GlobalProductResponseDto {
        val cleanEan = request.ean.trim()
        if (!globalProductService.isValidEan(cleanEan)) {
            throw InvalidRequestException("Código EAN inválido: '${request.ean}'. Deve conter 8, 12, 13 ou 14 dígitos.")
        }
        if (globalProductRepository.existsByEan(cleanEan)) {
            throw ConflictException("Já existe um produto canônico cadastrado para o EAN '$cleanEan'.")
        }

        val now = System.currentTimeMillis()
        val cleanName = request.name.trim()
        val cleanBrand = request.brand?.trim()?.takeIf { it.isNotEmpty() }
        val cleanCategory = request.category.trim().ifBlank { GlobalProductService.FIXED_GLOBAL_CATEGORY }

        val entity = GlobalProductEntity(
            id = DeterministicIdUtils.productIdForEan(cleanEan),
            ean = cleanEan,
            name = cleanName,
            normalizedName = DeterministicIdUtils.normalizeText(cleanName),
            brand = cleanBrand,
            category = cleanCategory,
            measureUnit = request.measureUnit,
            netWeight = request.netWeight,
            source = GlobalProductService.SOURCE_ADMIN_CURATED,
            occurrenceCount = 1L,
            isVerified = request.isVerified,
            createdAt = now,
            updatedAt = now
        )
        val saved = globalProductRepository.save(entity)

        adminAuditService.record(
            adminUsername = adminUsername,
            action = "GLOBAL_PRODUCT_CREATE",
            targetType = "GLOBAL_PRODUCT",
            targetId = cleanEan,
            details = "name='$cleanName', brand='$cleanBrand', unit=${request.measureUnit}, weight=${request.netWeight}",
            clientIp = clientIp
        )
        return GlobalProductResponseDto.fromEntity(saved)
    }

    @Transactional
    fun updateProduct(
        ean: String,
        request: AdminUpdateGlobalProductRequest,
        adminUsername: String,
        clientIp: String?
    ): GlobalProductResponseDto {
        val cleanEan = ean.trim()
        val existing = globalProductRepository.findByEan(cleanEan)
            .orElseThrow { ResourceNotFoundException("Produto global com EAN '$cleanEan' não encontrado.") }

        val oldName = existing.name
        val cleanName = request.name.trim()
        val cleanBrand = request.brand?.trim()?.takeIf { it.isNotEmpty() }
        val cleanCategory = request.category.trim().ifBlank { GlobalProductService.FIXED_GLOBAL_CATEGORY }

        existing.name = cleanName
        existing.normalizedName = DeterministicIdUtils.normalizeText(cleanName)
        existing.brand = cleanBrand
        existing.category = cleanCategory
        existing.measureUnit = request.measureUnit
        existing.netWeight = request.netWeight
        existing.source = GlobalProductService.SOURCE_ADMIN_CURATED
        existing.isVerified = request.isVerified
        existing.updatedAt = System.currentTimeMillis()

        val saved = globalProductRepository.save(existing)

        adminAuditService.record(
            adminUsername = adminUsername,
            action = "GLOBAL_PRODUCT_UPDATE",
            targetType = "GLOBAL_PRODUCT",
            targetId = cleanEan,
            details = "oldName='$oldName' -> newName='$cleanName', brand='$cleanBrand', verified=${request.isVerified}",
            clientIp = clientIp
        )
        return GlobalProductResponseDto.fromEntity(saved)
    }

    @Transactional
    fun enrichFromExternal(
        ean: String,
        adminUsername: String,
        clientIp: String?
    ): GlobalProductResponseDto {
        val cleanEan = ean.trim()
        if (!globalProductService.isValidEan(cleanEan)) {
            throw InvalidRequestException("Código EAN inválido: '$ean'.")
        }
        val client = openFactsCatalogClient
            ?: throw InvalidRequestException("Cliente Open Facts não configurado.")

        val external = client.lookupByEan(cleanEan)
            ?: throw ResourceNotFoundException("Produto com EAN '$cleanEan' não localizado nas bases públicas Open Facts.")

        val existing = globalProductRepository.findByEan(cleanEan).orElse(null)
        val now = System.currentTimeMillis()

        val saved = if (existing != null) {
            existing.name = external.name
            existing.normalizedName = DeterministicIdUtils.normalizeText(external.name)
            existing.brand = external.brand ?: existing.brand
            existing.measureUnit = external.measureUnit
            existing.netWeight = external.netWeight
            existing.source = external.source
            existing.isVerified = false
            existing.updatedAt = now
            globalProductRepository.save(existing)
        } else {
            globalProductRepository.save(
                GlobalProductEntity(
                    id = DeterministicIdUtils.productIdForEan(cleanEan),
                    ean = cleanEan,
                    name = external.name,
                    normalizedName = DeterministicIdUtils.normalizeText(external.name),
                    brand = external.brand,
                    category = GlobalProductService.FIXED_GLOBAL_CATEGORY,
                    measureUnit = external.measureUnit,
                    netWeight = external.netWeight,
                    source = external.source,
                    occurrenceCount = 1L,
                    isVerified = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        adminAuditService.record(
            adminUsername = adminUsername,
            action = "GLOBAL_PRODUCT_ENRICH_EXTERNAL",
            targetType = "GLOBAL_PRODUCT",
            targetId = cleanEan,
            details = "source=${external.source}, name='${external.name}'",
            clientIp = clientIp
        )
        return GlobalProductResponseDto.fromEntity(saved)
    }

    @Transactional
    fun deleteProduct(
        ean: String,
        adminUsername: String,
        clientIp: String?
    ) {
        val cleanEan = ean.trim()
        val existing = globalProductRepository.findByEan(cleanEan)
            .orElseThrow { ResourceNotFoundException("Produto global com EAN '$cleanEan' não encontrado.") }

        if (globalProductMediaRepository.existsById(cleanEan)) {
            globalProductMediaRepository.deleteById(cleanEan)
        }
        globalProductRepository.delete(existing)

        adminAuditService.record(
            adminUsername = adminUsername,
            action = "GLOBAL_PRODUCT_DELETE",
            targetType = "GLOBAL_PRODUCT",
            targetId = cleanEan,
            details = "deletedName='${existing.name}', source=${existing.source}",
            clientIp = clientIp
        )
    }

    @Transactional
    fun uploadMedia(
        ean: String,
        contentType: String?,
        bytes: ByteArray,
        adminUsername: String,
        clientIp: String?
    ): AdminProductMediaUploadResponseDto {
        val cleanEan = ean.trim()
        val existing = globalProductRepository.findByEan(cleanEan)
            .orElseThrow { ResourceNotFoundException("Produto global com EAN '$cleanEan' não encontrado.") }

        if (bytes.isEmpty()) {
            throw InvalidRequestException("O arquivo de imagem não pode estar vazio.")
        }
        if (bytes.size > MAX_MEDIA_BYTES) {
            throw InvalidRequestException("A imagem excede o limite máximo de 500 KB.")
        }

        val detectedType = detectImageType(bytes)
            ?: throw InvalidRequestException("Formato de imagem inválido. Envie uma imagem WebP, JPEG ou PNG válida.")

        val normalizedContentType = contentType?.trim()?.lowercase()?.takeIf { it in ALLOWED_CONTENT_TYPES } ?: detectedType
        val sha256 = sha256Hex(bytes)
        val now = System.currentTimeMillis()

        globalProductMediaRepository.save(
            GlobalProductMediaEntity(
                ean = cleanEan,
                contentType = normalizedContentType,
                sha256Hash = sha256,
                imageBytes = bytes,
                updatedAt = now
            )
        )

        existing.imageHash = sha256
        existing.updatedAt = now
        globalProductRepository.save(existing)

        adminAuditService.record(
            adminUsername = adminUsername,
            action = "GLOBAL_PRODUCT_MEDIA_PUT",
            targetType = "GLOBAL_PRODUCT",
            targetId = cleanEan,
            details = "contentType=$normalizedContentType, size=${bytes.size}, sha256=$sha256",
            clientIp = clientIp
        )

        logger.info("[Admin:Catalog] Updated canonical media for EAN={} (size={}, sha256={})", cleanEan, bytes.size, sha256)

        return AdminProductMediaUploadResponseDto(
            ean = cleanEan,
            sha256Hash = sha256,
            contentType = normalizedContentType,
            sizeBytes = bytes.size
        )
    }

    @Transactional
    fun deleteMedia(
        ean: String,
        adminUsername: String,
        clientIp: String?
    ) {
        val cleanEan = ean.trim()
        val existing = globalProductRepository.findByEan(cleanEan)
            .orElseThrow { ResourceNotFoundException("Produto global com EAN '$cleanEan' não encontrado.") }

        if (globalProductMediaRepository.existsById(cleanEan)) {
            globalProductMediaRepository.deleteById(cleanEan)
        }
        existing.imageHash = null
        existing.updatedAt = System.currentTimeMillis()
        globalProductRepository.save(existing)

        adminAuditService.record(
            adminUsername = adminUsername,
            action = "GLOBAL_PRODUCT_MEDIA_DELETE",
            targetType = "GLOBAL_PRODUCT",
            targetId = cleanEan,
            details = "Canonical media removed",
            clientIp = clientIp
        )
    }

    private fun detectImageType(bytes: ByteArray): String? {
        // WebP: RIFF....WEBP
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return "image/webp"
        }
        // JPEG: FF D8 FF
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        return null
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
