package com.langtutor.data.remote

import com.langtutor.data.remote.dto.ClaudeRequest
import com.langtutor.data.remote.dto.ClaudeResponse
import com.langtutor.data.security.ApiKeyStore
import com.langtutor.domain.repository.ErrorLogRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlin.random.Random

class KtorClaudeClient(
    private val httpClient: HttpClient,
    private val apiKeyStore: ApiKeyStore,
    private val errorLogRepository: ErrorLogRepository,
) : ClaudeClient {

    companion object {
        private const val BASE_URL = "https://api.anthropic.com"
        private const val API_VERSION = "2023-06-01"
        private const val MAX_RETRIES = 2
    }

    override suspend fun send(request: ClaudeRequest): Result<ClaudeResponse> {
        val key = apiKeyStore.get() ?: return Result.failure(ClaudeError.InvalidApiKey())
        return executeWithRetry {
            val response = httpClient.post("$BASE_URL/v1/messages") {
                header("x-api-key", key)
                header("anthropic-version", API_VERSION)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            mapResponse(response)
        }
    }

    override suspend fun validateKey(key: String): Result<Unit> {
        return try {
            val response = httpClient.get("$BASE_URL/v1/models?limit=1") {
                header("x-api-key", key)
                header("anthropic-version", API_VERSION)
            }
            when (response.status.value) {
                200 -> Result.success(Unit)
                401, 403 -> Result.failure(ClaudeError.InvalidApiKey())
                else -> Result.failure(ClaudeError.BadRequest("HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(ClaudeError.Network(e))
        }
    }

    private suspend fun mapResponse(response: HttpResponse): ClaudeResponse = when (response.status.value) {
        200 -> response.body()
        401, 403 -> throw ClaudeError.InvalidApiKey()
        429 -> {
            val retryAfter = response.headers["retry-after"]?.toIntOrNull()
            throw ClaudeError.RateLimited(retryAfter)
        }
        400 -> {
            val body = runCatching { response.bodyAsText() }.getOrNull()
            throw ClaudeError.BadRequest(body)
        }
        // 529 = Anthropic overloaded; treat 5xx the same
        else -> throw ClaudeError.Overloaded()
    }

    private suspend fun <T> executeWithRetry(block: suspend () -> T): Result<T> {
        var attempt = 0
        while (true) {
            try {
                return Result.success(block())
            } catch (e: ClaudeError.RateLimited) {
                if (attempt >= MAX_RETRIES) return logAndFail(e)
                val waitMs = ((e.retryAfterSeconds ?: 2) * 1000L) + Random.nextLong(0, 500)
                delay(waitMs)
            } catch (e: ClaudeError.Overloaded) {
                if (attempt >= MAX_RETRIES) return logAndFail(e)
                delay(1000L * (attempt + 1) + Random.nextLong(0, 500))
            } catch (e: ClaudeError.Network) {
                if (attempt >= MAX_RETRIES) return logAndFail(e)
                delay(1000L * (attempt + 1) + Random.nextLong(0, 500))
            } catch (e: ClaudeError) {
                return logAndFail(e)
            } catch (e: Exception) {
                return Result.failure(e)
            }
            attempt++
        }
    }

    private suspend fun <T> logAndFail(e: ClaudeError): Result<T> {
        // Log silently — a logging failure must never surface to the caller
        runCatching {
            errorLogRepository.log(
                errorType = e::class.simpleName ?: "Unknown",
                message = e.message ?: e.toString(),
            )
        }
        return Result.failure(e)
    }
}
