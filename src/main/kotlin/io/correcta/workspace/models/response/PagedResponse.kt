package io.correcta.workspace.models.response

/** One page of results with its paging metadata. */
data class PagedResponse<T>(
  val content: List<T>,
  val page: Int,
  val size: Int,
  val totalElements: Long,
  val totalPages: Int,
  val sortBy: String,
  val sortDirection: String,
)