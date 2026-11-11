package io.correcta.workspace.models.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/** Identifies the question a workspace is created for. */
data class CreateWorkspaceRequest(
  @field:Schema(description = "Assessment id.")
  @field:NotBlank(message = "A avaliação é obrigatória")
  val assessmentId: String,

  @field:Schema(description = "Assessment question id.")
  @field:NotBlank(message = "A questão é obrigatória")
  val questionId: String,
)