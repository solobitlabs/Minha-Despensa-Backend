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

package com.bitlabbr.minhadespensa.modules.admin.controller

import com.bitlabbr.minhadespensa.common.dto.ApiResponse
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminCreateGlobalProductRequest
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminPagedResponseDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminProductMediaUploadResponseDto
import com.bitlabbr.minhadespensa.modules.admin.dto.AdminUpdateGlobalProductRequest
import com.bitlabbr.minhadespensa.modules.admin.service.AdminCatalogService
import com.bitlabbr.minhadespensa.modules.catalog.dto.GlobalProductResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/admin/products")
@Tag(name = "Admin - Catálogo Canônico", description = "Gerenciamento, sanitização e mídia do catálogo global (requer SYSTEM_ADMIN)")
@SecurityRequirement(name = "bearerAuth")
class AdminCatalogController(
    private val adminCatalogService: AdminCatalogService
) {

    @GetMapping
    @Operation(summary = "Listar produtos canônicos com filtros e paginação")
    fun listProducts(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) source: String?,
        @RequestParam(required = false) verified: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): ApiResponse<AdminPagedResponseDto<GlobalProductResponseDto>> {
        return ApiResponse.ok(adminCatalogService.listProducts(query, source, verified, page, size))
    }

    @PostMapping
    @Operation(summary = "Cadastrar novo produto canônico manualmente")
    fun createProduct(
        @Valid @RequestBody request: AdminCreateGlobalProductRequest,
        @AuthenticationPrincipal jwt: Jwt?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<GlobalProductResponseDto>> {
        val created = adminCatalogService.createProduct(
            request = request,
            adminUsername = jwt?.subject ?: "admin",
            clientIp = resolveClientIp(httpRequest)
        )
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(created, "Produto canônico criado com sucesso."))
    }

    @PutMapping("/{ean}")
    @Operation(summary = "Sanitizar/editar informações canônicas de um produto")
    fun updateProduct(
        @PathVariable ean: String,
        @Valid @RequestBody request: AdminUpdateGlobalProductRequest,
        @AuthenticationPrincipal jwt: Jwt?,
        httpRequest: HttpServletRequest
    ): ApiResponse<GlobalProductResponseDto> {
        val updated = adminCatalogService.updateProduct(
            ean = ean,
            request = request,
            adminUsername = jwt?.subject ?: "admin",
            clientIp = resolveClientIp(httpRequest)
        )
        return ApiResponse.ok(updated, "Produto sanitizado e atualizado com sucesso.")
    }

    @PostMapping("/{ean}/enrich")
    @Operation(summary = "Buscar e atualizar produto a partir das APIs públicas Open Facts")
    fun enrichFromExternal(
        @PathVariable ean: String,
        @AuthenticationPrincipal jwt: Jwt?,
        httpRequest: HttpServletRequest
    ): ApiResponse<GlobalProductResponseDto> {
        val enriched = adminCatalogService.enrichFromExternal(
            ean = ean,
            adminUsername = jwt?.subject ?: "admin",
            clientIp = resolveClientIp(httpRequest)
        )
        return ApiResponse.ok(enriched, "Produto atualizado com dados do Open Facts.")
    }

    @DeleteMapping("/{ean}")
    @Operation(summary = "Remover produto do catálogo canônico")
    fun deleteProduct(
        @PathVariable ean: String,
        @AuthenticationPrincipal jwt: Jwt?,
        httpRequest: HttpServletRequest
    ): ApiResponse<Unit> {
        adminCatalogService.deleteProduct(
            ean = ean,
            adminUsername = jwt?.subject ?: "admin",
            clientIp = resolveClientIp(httpRequest)
        )
        return ApiResponse.ok(Unit, "Produto removido do catálogo canônico.")
    }

    @GetMapping("/{ean}/media")
    @Operation(summary = "Obter preview da foto do produto (canônica ou fallback de casa)")
    fun getMediaPreview(@PathVariable ean: String): ResponseEntity<ByteArray> {
        val media = adminCatalogService.getMediaPreview(ean)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(media.contentType))
            .header("X-Media-Origin", media.origin)
            .eTag("\"${media.sha256Hash}\"")
            .body(media.imageBytes)
    }

    @PostMapping("/{ean}/media/promote")
    @Operation(summary = "Promover foto enviada por usuário como foto canônica global")
    fun promoteMedia(
        @PathVariable ean: String,
        @AuthenticationPrincipal jwt: Jwt?,
        httpRequest: HttpServletRequest
    ): ApiResponse<AdminProductMediaUploadResponseDto> {
        val promoted = adminCatalogService.promoteHouseholdMediaToCanonical(
            ean = ean,
            adminUsername = jwt?.subject ?: "admin",
            clientIp = resolveClientIp(httpRequest)
        )
        return ApiResponse.ok(promoted, "Foto promovida para o catálogo canônico.")
    }

    @PutMapping("/{ean}/media", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Enviar ou substituir foto canônica do produto (WebP/JPEG/PNG até 500KB)")
    fun uploadMedia(
        @PathVariable ean: String,
        @RequestPart("file") file: MultipartFile,
        @AuthenticationPrincipal jwt: Jwt?,
        httpRequest: HttpServletRequest
    ): ApiResponse<AdminProductMediaUploadResponseDto> {
        val uploaded = adminCatalogService.uploadMedia(
            ean = ean,
            contentType = file.contentType,
            bytes = file.bytes,
            adminUsername = jwt?.subject ?: "admin",
            clientIp = resolveClientIp(httpRequest)
        )
        return ApiResponse.ok(uploaded, "Foto canônica atualizada com sucesso.")
    }

    @DeleteMapping("/{ean}/media")
    @Operation(summary = "Remover foto canônica do produto")
    fun deleteMedia(
        @PathVariable ean: String,
        @AuthenticationPrincipal jwt: Jwt?,
        httpRequest: HttpServletRequest
    ): ApiResponse<Unit> {
        adminCatalogService.deleteMedia(
            ean = ean,
            adminUsername = jwt?.subject ?: "admin",
            clientIp = resolveClientIp(httpRequest)
        )
        return ApiResponse.ok(Unit, "Foto canônica removida.")
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(",").first().trim()
        }
        return request.remoteAddr ?: "unknown"
    }
}
