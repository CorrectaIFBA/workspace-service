package io.correcta.workspace.models.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Largest accepted source code, in characters. */
const val MAX_SOURCE_CODE_LENGTH = 65_536

/** Body of `PUT /workspaces/{workspaceId}/drafts`. */
data class SaveDraftRevisionRequest(
  @field:Schema(description = "Submitted source code.")
  @field:NotBlank(message = "O código-fonte é obrigatório")
  @field:Size(max = MAX_SOURCE_CODE_LENGTH, message = "O código-fonte excede o tamanho máximo de $MAX_SOURCE_CODE_LENGTH caracteres")
  val sourceCode: String,

  @field:Schema(description = "Programming language of the source code.", example = "C")
  @field:NotBlank(message = "A linguagem é obrigatória")
  val language: String,
)