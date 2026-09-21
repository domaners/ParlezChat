package com.langtutor.data.remote

sealed class ClaudeError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidApiKey : ClaudeError("API key was not accepted")
    class RateLimited(val retryAfterSeconds: Int?) : ClaudeError("Rate limited by Anthropic API")
    class Overloaded : ClaudeError("Anthropic API overloaded")
    class Network(cause: Throwable?) : ClaudeError("Network error", cause)
    class BadRequest(val detail: String?) : ClaudeError("Bad request: $detail")
    class MalformedResponse(val detail: String?) : ClaudeError("Malformed response: $detail")
}
