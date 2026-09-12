package com.olivia.core

import java.net.URI
import java.net.ServerSocket
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.io.IOException

class ProviderInfrastructureTest {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

    @Test
    fun realJvmTransportSeesProviderScenarios() {
        val base = System.getenv("OLIVIA_STUB_URL")
        assertEquals("http://provider-stub:8080", base)
        for ((scenario, status) in mapOf("ok" to 200, "unauthorized" to 401, "forbidden" to 403, "rate-limit" to 429, "unavailable" to 503)) {
            val request = HttpRequest.newBuilder(URI.create("$base/scenarios/$scenario"))
                .timeout(Duration.ofSeconds(2)).POST(HttpRequest.BodyPublishers.noBody()).build()
            assertEquals(status, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode())
        }
        assertFailsWith<HttpTimeoutException> {
            client.send(HttpRequest.newBuilder(URI.create("$base/scenarios/timeout"))
                .timeout(Duration.ofMillis(100)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding())
        }
    }

    @Test
    fun unavailableEndpointDoesNotBecomeSuccess() {
        val port = ServerSocket(0).use { it.localPort }
        assertFailsWith<IOException> {
            client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/health"))
                .timeout(Duration.ofSeconds(1)).build(), HttpResponse.BodyHandlers.discarding())
        }
    }
}
