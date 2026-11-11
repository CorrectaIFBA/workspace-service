package io.correcta.workspace.models.response.error

/** Error body of a failed validation: one message per invalid field. */
data class ValidationErrorResponse(
  val message: String,
  val errors: Map<String, String>,
)