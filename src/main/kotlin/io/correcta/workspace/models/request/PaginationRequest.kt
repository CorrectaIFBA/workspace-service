package io.correcta.workspace.models.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern

data class PaginationRequest(
  @field:Schema(description = "Zero-based page index.", defaultValue = "0")
  @field:Min(value = 0, message = "A página deve ser maior ou igual a 0")
  val page: Int = 0,

  @field:Schema(description = "Number of items per page.", defaultValue = "20")
  @field:Min(value = 1, message = "O tamanho da página deve ser no mínimo 1")
  @field:Max(value = 100, message = "O tamanho da página deve ser no máximo 100")
  val size: Int = 20,

  @field:Schema(description = "Field used to sort the results.", defaultValue = "createdAt")
  val sortBy: String = "createdAt",

  @field:Schema(description = "Sort direction.", allowableValues = ["asc", "desc"], defaultValue = "desc")
  @field:Pattern(regexp = "(?i)asc|desc", message = "A direção de ordenação deve ser 'asc' ou 'desc'")
  val sortDirection: String = "desc",
)