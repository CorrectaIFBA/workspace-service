package io.correcta.workspace.components

import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VaryHeaderSuppressingFilterTest {

  @Test
  fun `drops every Vary header and keeps the others`() {
    val response = MockHttpServletResponse()
    response.addHeader("Vary", "Origin")
    var visibleHeaders: Collection<String> = emptyList()

    VaryHeaderSuppressingFilter().doFilter(MockHttpServletRequest(), response) { _, wrapped ->
      wrapped as HttpServletResponse
      wrapped.addHeader("vary", "Accept")
      wrapped.setHeader("VARY", "Accept-Encoding")
      wrapped.addHeader("X-Added", "1")
      wrapped.setHeader("X-Set", "2")
      visibleHeaders = wrapped.headerNames
    }

    assertEquals(listOf("Origin"), response.getHeaders("Vary"))
    assertEquals("1", response.getHeader("X-Added"))
    assertEquals("2", response.getHeader("X-Set"))
    assertEquals(setOf("X-Added", "X-Set"), visibleHeaders.toSet())
    assertNull(visibleHeaders.firstOrNull { it.equals("Vary", ignoreCase = true) })
  }
}