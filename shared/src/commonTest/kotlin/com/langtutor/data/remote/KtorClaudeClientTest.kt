package com.langtutor.data.remote

import com.langtutor.data.remote.dto.ClaudeRequest
import com.langtutor.data.remote.dto.MessageDto
import com.langtutor.data.security.ApiKeyStore
import com.langtutor.data.security.SecureKeyStore
import com.langtutor.domain.model.ApiErrorLog
import com.langtutor.domain.repository.ErrorLogRepository
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KtorClaudeClientTest {

    private fun buildClient(handler: MockRequestHandler): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun apiKeyStore(key: String? = "sk-ant-test") = ApiKeyStore(object : SecureKeyStore {
        override suspend fun put(alias: String, value: String) {}
        override suspend fun get(alias: String) = key
        override suspend fun delete(alias: String) {}
    })

    private fun errorLogRepository() = object : ErrorLogRepository {
        override fun observeAll(): Flow<List<ApiErrorLog>> = flowOf(emptyList())
        override suspend fun log(errorType: String, message: String) {}
        override suspend fun clearAll() {}
    }

    private val validResponseBody = """
        {"id":"m1","type":"message","role":"assistant",
         "content":[{"type":"text","text":"Hola"}],
         "stop_reason":"end_turn","usage":{"input_tokens":5,"output_tokens":3}}
    """.trimIndent()

    private val testRequest = ClaudeRequest(
        model = "claude-sonnet-4-6",
        maxTokens = 400,
        messages = listOf(MessageDto("user", "Hello")),
    )

    @Test
    fun `returns success on 200`() = runTest {
        val client = buildClient { respond(validResponseBody, HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) }
        val result = KtorClaudeClient(client, apiKeyStore(), errorLogRepository()).send(testRequest)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `maps 401 to InvalidApiKey`() = runTest {
        val client = buildClient { respond("{}", HttpStatusCode.Unauthorized) }
        val result = KtorClaudeClient(client, apiKeyStore(), errorLogRepository()).send(testRequest)
        assertIs<ClaudeError.InvalidApiKey>(result.exceptionOrNull())
    }

    @Test
    fun `maps 429 to RateLimited with retry-after`() = runTest {
        val client = buildClient {
            respond("{}", HttpStatusCode.TooManyRequests,
                headers = headersOf("retry-after", "30"))
        }
        val result = KtorClaudeClient(client, apiKeyStore(), errorLogRepository()).send(testRequest)
        val err = result.exceptionOrNull()
        assertIs<ClaudeError.RateLimited>(err)
        // Actual retry logic has up to 2 retries, but MockEngine always returns 429
        // so after MAX_RETRIES attempts it returns failure
    }

    @Test
    fun `returns InvalidApiKey when no key stored`() = runTest {
        val client = buildClient { respond("{}", HttpStatusCode.OK) }
        val result = KtorClaudeClient(client, apiKeyStore(null), errorLogRepository()).send(testRequest)
        assertIs<ClaudeError.InvalidApiKey>(result.exceptionOrNull())
    }

    @Test
    fun `validateKey returns success on 200`() = runTest {
        val client = buildClient { respond("""{"data":[]}""", HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) }
        val result = KtorClaudeClient(client, apiKeyStore(), errorLogRepository()).validateKey("sk-ant-test")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `validateKey returns InvalidApiKey on 401`() = runTest {
        val client = buildClient { respond("{}", HttpStatusCode.Unauthorized) }
        val result = KtorClaudeClient(client, apiKeyStore(), errorLogRepository()).validateKey("bad-key")
        assertIs<ClaudeError.InvalidApiKey>(result.exceptionOrNull())
    }
}
