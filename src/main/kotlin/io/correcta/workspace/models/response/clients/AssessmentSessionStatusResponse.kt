package io.correcta.workspace.models.response.clients

/** Session status returned by assessment-service. [late] is true when the deadline has passed. */
data class AssessmentSessionStatusResponse(
  val sessionId: String,
  val state: String,
  val expiresAt: String?,
  val finishedAt: String?,
  val acceptsSubmissions: Boolean,
  val late: Boolean,
)