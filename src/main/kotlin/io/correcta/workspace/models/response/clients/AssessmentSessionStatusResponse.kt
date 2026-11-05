package io.correcta.workspace.models.response.clients

data class AssessmentSessionStatusResponse(
    val sessionId: String,
    val state: String,
    val expiresAt: String?,
    val finishedAt: String?,
    val acceptsSubmissions: Boolean,
    val late: Boolean
)
