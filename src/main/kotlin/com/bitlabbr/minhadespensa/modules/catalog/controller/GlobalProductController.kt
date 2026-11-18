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

package com.bitlabbr.minhadespensa.modules.catalog.controller

import com.bitlabbr.minhadespensa.common.dto.ApiResponse
import com.bitlabbr.minhadespensa.modules.catalog.dto.GlobalProductResponseDto
import com.bitlabbr.minhadespensa.modules.catalog.service.GlobalProductService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/v1/catalog/global")
@Tag(name = "Catálogo Global", description = "Endpoints de consulta ao catálogo centralizado e consolidado de produtos por EAN")
@SecurityRequirement(name = "BearerAuth")
class GlobalProductController(
    private val globalProductService: GlobalProductService
) {

    @GetMapping("/{ean}")
    @Operation(
        summary = "Consultar produto global por EAN",
        description = "Retorna os metadados do produto por código de barras. Quando X-Household-Id é informado e a casa já possui o produto, retorna a versão customizada da casa; caso contrário, consulta o catálogo canônico global e bases públicas Open Facts."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Produto localizado com sucesso."),
            SwaggerApiResponse(responseCode = "400", description = "Formato de código EAN inválido."),
            SwaggerApiResponse(responseCode = "401", description = "Token JWT ausente ou expirado."),
            SwaggerApiResponse(responseCode = "403", description = "Usuário sem acesso à casa informada em X-Household-Id."),
            SwaggerApiResponse(responseCode = "404", description = "Produto não localizado no catálogo central nem nas bases externas.")
        ]
    )
    fun getProductByEan(
        @Parameter(description = "Código de barras universal EAN (8, 12, 13 ou 14 dígitos)", example = "7891000100103")
        @PathVariable ean: String,
        @Parameter(description = "ID opcional da casa para priorizar customizações locais do usuário")
        @RequestHeader(name = "X-Household-Id", required = false) householdId: String?,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<ApiResponse<GlobalProductResponseDto>> {
        val product = globalProductService.getProductByEan(
            ean = ean,
            userId = jwt?.subject,
            householdId = householdId
        )
        return ResponseEntity.ok(ApiResponse.ok(product))
    }

    @GetMapping("/search")
    @Operation(
        summary = "Pesquisar produtos no catálogo global",
        description = "Realiza busca aproximada por nome, marca ou início de código EAN, priorizando produtos customizados da casa quando X-Household-Id é informado."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Lista de produtos localizados."),
            SwaggerApiResponse(responseCode = "401", description = "Token JWT ausente ou expirado."),
            SwaggerApiResponse(responseCode = "403", description = "Usuário sem acesso à casa informada em X-Household-Id.")
        ]
    )
    fun searchProducts(
        @Parameter(description = "Termo de busca (mínimo de 2 caracteres)", example = "Leite")
        @RequestParam(name = "query") query: String,
        @Parameter(description = "Limite máximo de registros a retornar (entre 1 e 50)", example = "20")
        @RequestParam(name = "limit", defaultValue = "20") limit: Int,
        @Parameter(description = "ID opcional da casa para priorizar customizações locais do usuário")
        @RequestHeader(name = "X-Household-Id", required = false) householdId: String?,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<ApiResponse<List<GlobalProductResponseDto>>> {
        val results = globalProductService.searchProducts(
            query = query,
            limit = limit,
            userId = jwt?.subject,
            householdId = householdId
        )
        return ResponseEntity.ok(ApiResponse.ok(results))
    }

    @GetMapping("/{ean}/media")
    @Operation(
        summary = "Obter foto canônica do produto global",
        description = "Retorna os bytes da imagem canônica do produto no catálogo global com suporte a cache ETag / 304 Not Modified."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Imagem retornada com sucesso."),
            SwaggerApiResponse(responseCode = "304", description = "Imagem não modificada (ETag correspondente)."),
            SwaggerApiResponse(responseCode = "400", description = "Formato de código EAN inválido."),
            SwaggerApiResponse(responseCode = "401", description = "Token JWT ausente ou expirado."),
            SwaggerApiResponse(responseCode = "404", description = "Produto não possui foto canônica cadastrada.")
        ]
    )
    fun getGlobalProductMedia(
        @PathVariable ean: String,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?
    ): ResponseEntity<ByteArray> {
        val media = globalProductService.getGlobalMedia(ean)
        val eTag = "\"${media.sha256Hash}\""
        val cleanIfNoneMatch = ifNoneMatch?.trim()?.removeSurrounding("\"")
        if (cleanIfNoneMatch != null && cleanIfNoneMatch == media.sha256Hash) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(eTag)
                .build()
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(media.contentType))
            .eTag(eTag)
            .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
            .body(media.imageBytes)
    }
}
