package io.correcta.workspace.security

import io.correcta.workspace.security.filter.HeaderAuthenticationFilter
import io.correcta.workspace.security.models.IdentityHeaders
import io.correcta.workspace.security.models.ProfilePrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HeaderAuthenticationFilterTest {

  private val filter = HeaderAuthenticationFilter()

  @AfterEach
  fun clear() = SecurityContextHolder.clearContext()

  @Test
  fun `identity headers become the authenticated principal`() {
    val request = MockHttpServletRequest("GET", "/workspaces")
    request.addHeader(IdentityHeaders.PROFILE_ID, " p1 ")
    request.addHeader(IdentityHeaders.PERSON_ID, "person-1")
    request.addHeader(IdentityHeaders.PROFILE_TYPE, "STUDENT")
    request.addHeader(IdentityHeaders.LOGIN, "login")
    request.addHeader(IdentityHeaders.EMAIL, " ")
    val chain = MockFilterChain()

    filter.doFilter(request, MockHttpServletResponse(), chain)

    val authentication = SecurityContextHolder.getContext().authentication
    assertEquals(ProfilePrincipal("p1", "person-1", "STUDENT", "login", null), authentication.principal)
    assertEquals(true, authentication.isAuthenticated)
    assertNotNull(chain.request)
  }

  @Test
  fun `requests without a profile id stay anonymous`() {
    val chain = MockFilterChain()

    filter.doFilter(MockHttpServletRequest("GET", "/workspaces"), MockHttpServletResponse(), chain)

    assertNull(SecurityContextHolder.getContext().authentication)
    assertNotNull(chain.request)
  }

  @Test
  fun `preflight requests are not authenticated`() {
    val request = MockHttpServletRequest("OPTIONS", "/workspaces")
    request.addHeader(IdentityHeaders.PROFILE_ID, "p1")

    filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

    assertNull(SecurityContextHolder.getContext().authentication)
  }
}