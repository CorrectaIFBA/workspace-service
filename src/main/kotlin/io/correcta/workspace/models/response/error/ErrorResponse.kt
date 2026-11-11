package io.correcta.workspace.models.response.error

/** Error body with a single message. */
data class ErrorResponse(
  val message: String,
)