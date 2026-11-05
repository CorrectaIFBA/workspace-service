package io.correcta.workspace.models.response.error

data class ValidationErrorResponse(
    val message: String,
    val errors: Map<String, String>
)