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

package com.bitlabbr.minhadespensa.modules.catalog.client

import com.bitlabbr.minhadespensa.modules.catalog.domain.MeasureUnit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class ExternalCatalogProduct(
    val ean: String,
    val name: String,
    val brand: String?,
    val measureUnit: MeasureUnit,
    val netWeight: Double,
    val source: String
)

internal data class OpenFactsEndpoint(
    val sourceName: String,
    val baseUrl: String
)

@Component
class OpenFactsCatalogClient(
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
) {
    private val logger = LoggerFactory.getLogger(OpenFactsCatalogClient::class.java)

    companion object {
        private const val USER_AGENT = "MinhaDespensaBackend/2.3.0 (contato@bitlabbr.com)"
        private const val REQUEST_TIMEOUT_SECONDS = 4L
        private val QUANTITY_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*([a-zA-Z]+)""")

        private val ENDPOINTS = listOf(
            OpenFactsEndpoint("OPEN_FOOD_FACTS", "https://world.openfoodfacts.org/api/v2/product"),
            OpenFactsEndpoint("OPEN_BEAUTY_FACTS", "https://world.openbeautyfacts.org/api/v2/product"),
            OpenFactsEndpoint("OPEN_PRODUCTS_FACTS", "https://world.openproductsfacts.org/api/v2/product")
        )
    }

    fun lookupByEan(ean: String): ExternalCatalogProduct? {
        val cleanEan = ean.trim()
        for (endpoint in ENDPOINTS) {
            val url = "${endpoint.baseUrl}/$cleanEan.json"
            val body = fetchJson(url) ?: continue
            val parsed = parseResponse(cleanEan, body, endpoint.sourceName)
            if (parsed != null) {
                logger.info(
                    "[Catalog:External] Hit on {} for EAN={} -> '{}' (brand='{}', unit={}, weight={})",
                    endpoint.sourceName,
                    cleanEan,
                    parsed.name,
                    parsed.brand,
                    parsed.measureUnit,
                    parsed.netWeight
                )
                return parsed
            }
        }
        logger.debug("[Catalog:External] Miss across all Open Facts providers for EAN={}", cleanEan)
        return null
    }

    internal fun fetchJson(url: String): String? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                response.body()
            } else {
                logger.debug("[Catalog:External] HTTP {} from {}", response.statusCode(), url)
                null
            }
        } catch (ex: Exception) {
            logger.warn("[Catalog:External] Request failed for {}: {}", url, ex.message)
            null
        }
    }

    internal fun parseResponse(ean: String, jsonBody: String, sourceName: String): ExternalCatalogProduct? {
        return try {
            val root = objectMapper.readTree(jsonBody)
            if (root.path("status").asInt(0) != 1) {
                return null
            }
            val product = root.path("product")
            if (product.isMissingNode || product.isNull) {
                return null
            }

            val name = extractBestName(product) ?: return null
            val brand = extractBrand(product)
            val (unit, netWeight) = extractMeasureAndWeight(product)

            ExternalCatalogProduct(
                ean = ean,
                name = name.take(255),
                brand = brand?.take(80),
                measureUnit = unit,
                netWeight = netWeight,
                source = sourceName
            )
        } catch (ex: Exception) {
            logger.warn("[Catalog:External] Failed to parse JSON from {} for EAN={}: {}", sourceName, ean, ex.message)
            null
        }
    }

    private fun extractBestName(product: JsonNode): String? {
        val candidates = listOf(
            "product_name_pt",
            "product_name",
            "generic_name_pt",
            "generic_name"
        )
        for (field in candidates) {
            val value = product.path(field).asString("").trim()
            if (value.isNotEmpty()) {
                return value
            }
        }
        return null
    }

    private fun extractBrand(product: JsonNode): String? {
        val rawBrands = product.path("brands").asString("").trim()
        if (rawBrands.isEmpty()) return null
        return rawBrands.split(",").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractMeasureAndWeight(product: JsonNode): Pair<MeasureUnit, Double> {
        val qtyValue = product.path("product_quantity").asString("").trim().replace(",", ".").toDoubleOrNull()
        val qtyUnitRaw = product.path("product_quantity_unit").asString("").trim()
        if (qtyValue != null && qtyValue > 0.0 && qtyUnitRaw.isNotEmpty()) {
            return mapUnit(qtyUnitRaw) to qtyValue
        }

        val quantityText = product.path("quantity").asString("").trim()
        val match = QUANTITY_REGEX.find(quantityText)
        if (match != null) {
            val value = match.groupValues[1].replace(",", ".").toDoubleOrNull()
            val unitStr = match.groupValues[2]
            if (value != null && value > 0.0) {
                return mapUnit(unitStr) to value
            }
        }

        return MeasureUnit.UNIT to 1.0
    }

    private fun mapUnit(rawUnit: String): MeasureUnit {
        return when (rawUnit.trim().lowercase()) {
            "g", "gr", "gram", "grams", "grama", "gramas" -> MeasureUnit.GRAM
            "kg", "kilo", "kilogram", "kilograms", "quilo" -> MeasureUnit.KILOGRAM
            "ml", "milliliter", "milliliters", "mililitro" -> MeasureUnit.MILLILITER
            "l", "lt", "liter", "liters", "litro", "litros" -> MeasureUnit.LITER
            "cl" -> MeasureUnit.MILLILITER
            "un", "und", "unit", "units", "pcs", "pc" -> MeasureUnit.UNIT
            "pct", "pacote", "pack", "pkg", "cx", "box" -> MeasureUnit.PACKAGE
            else -> MeasureUnit.UNIT
        }
    }
}
