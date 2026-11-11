package io.correcta.workspace.utils

import io.correcta.workspace.exceptions.BadRequestException
import io.correcta.workspace.models.request.PaginationRequest
import io.correcta.workspace.utils.PaginationExt.toPageRequest
import io.correcta.workspace.utils.PaginationExt.toPagedResponse
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaginationExtTest {

  @Test
  fun `defaults sort by newest first`() {
    val request = PaginationRequest()

    assertEquals(
      listOf<Any>(0, 20, "createdAt", "desc"),
      listOf(request.page, request.size, request.sortBy, request.sortDirection),
    )
    assertEquals(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), request.toPageRequest(setOf("createdAt")))
  }

  @Test
  fun `rejects a sort field outside the allowed set`() {
    val request = PaginationRequest(sortBy = "password")

    val e = assertFailsWith<BadRequestException> { request.toPageRequest(setOf("createdAt", "revision")) }

    assertEquals("Não é possível ordenar por 'password'. Campos permitidos: createdAt, revision", e.message)
  }

  @Test
  fun `maps a page and keeps the paging metadata`() {
    val request = PaginationRequest(page = 1, size = 2, sortBy = "revision", sortDirection = "ASC")
    val page = PageImpl(listOf(3, 4), request.toPageRequest(setOf("revision")), 5)

    val response = page.toPagedResponse(request) { "r$it" }

    assertEquals(listOf("r3", "r4"), response.content)
    assertEquals(
      listOf<Any>(1, 2, 5L, 3, "revision", "asc"),
      listOf(response.page, response.size, response.totalElements, response.totalPages, response.sortBy, response.sortDirection),
    )
  }
}