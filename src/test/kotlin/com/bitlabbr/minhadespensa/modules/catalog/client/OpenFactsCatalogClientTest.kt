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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class OpenFactsCatalogClientTest {

    private val objectMapper = ObjectMapper()

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `lookupByEan should parse Open Food Facts response with product_name_pt and structured quantity`() {
        val httpClient: HttpClient = mock()
        val response: HttpResponse<String> = mock()

        val json = """
            {
              "status": 1,
              "product": {
                "product_name_pt": "Leite Condensado Moça",
                "product_name": "Condensed Milk",
                "brands": "Nestlé, Moça",
                "product_quantity": "395",
                "product_quantity_unit": "g"
              }
            }
        """.trimIndent()

        whenever(response.statusCode()).thenReturn(200)
        whenever(response.body()).thenReturn(json)
        whenever(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>()))
            .thenReturn(response)

        val client = OpenFactsCatalogClient(objectMapper, httpClient)
        val result = client.lookupByEan("7891000100103")

        assertNotNull(result)
        assertEquals("7891000100103", result!!.ean)
        assertEquals("Leite Condensado Moça", result.name)
        assertEquals("Nestlé", result.brand)
        assertEquals(MeasureUnit.GRAM, result.measureUnit)
        assertEquals(395.0, result.netWeight)
        assertEquals("OPEN_FOOD_FACTS", result.source)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `lookupByEan should cascade to Open Beauty Facts when Open Food Facts misses`() {
        val httpClient: HttpClient = mock()
        val missResponse: HttpResponse<String> = mock()
        val hitResponse: HttpResponse<String> = mock()

        whenever(missResponse.statusCode()).thenReturn(200)
        whenever(missResponse.body()).thenReturn("""{"status": 0}""")

        whenever(hitResponse.statusCode()).thenReturn(200)
        whenever(hitResponse.body()).thenReturn(
            """
            {
              "status": 1,
              "product": {
                "product_name": "Sabonete Líquido",
                "brands": "Dove",
                "quantity": "250 ml"
              }
            }
            """.trimIndent()
        )

        whenever(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>()))
            .thenAnswer { invocation ->
                val req = invocation.getArgument<HttpRequest>(0)
                if (req.uri().host.contains("openbeautyfacts")) hitResponse else missResponse
            }

        val client = OpenFactsCatalogClient(objectMapper, httpClient)
        val result = client.lookupByEan("7891150050123")

        assertNotNull(result)
        assertEquals("Sabonete Líquido", result!!.name)
        assertEquals("Dove", result.brand)
        assertEquals(MeasureUnit.MILLILITER, result.measureUnit)
        assertEquals(250.0, result.netWeight)
        assertEquals("OPEN_BEAUTY_FACTS", result.source)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `lookupByEan should return null when all providers miss or fail`() {
        val httpClient: HttpClient = mock()
        whenever(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>()))
            .thenThrow(RuntimeException("Connection timeout"))

        val client = OpenFactsCatalogClient(objectMapper, httpClient)
        val result = client.lookupByEan("7899999999999")

        assertNull(result)
    }
}
