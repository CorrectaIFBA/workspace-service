package io.correcta.workspace.security

import io.correcta.workspace.security.components.ScopeEvaluatorComponent
import io.correcta.workspace.security.models.ProfilePrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScopeEvaluatorComponentTest {

  private val evaluator = ScopeEvaluatorComponent()
  private val student = ProfilePrincipal("p1", "person-1", "STUDENT", "login", "a@b.c")

  @AfterEach
  fun clear() = SecurityContextHolder.clearContext()

  @Test
  fun `no authentication means no profile`() {
    assertFalse(evaluator.isLoggedUser())
    assertFalse(evaluator.isProfileType("STUDENT"))
    assertNull(evaluator.currentProfile())
  }

  @Test
  fun `authenticated profile is logged in and matches its type ignoring case`() {
    SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(student, null, emptyList())

    assertTrue(evaluator.isLoggedUser())
    assertTrue(evaluator.isProfileType("student"))
    assertFalse(evaluator.isProfileType("FACULTY"))
    assertEquals(student, evaluator.currentProfile())
  }

  @Test
  fun `unauthenticated token is ignored`() {
    SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(student, null)

    assertNull(evaluator.currentProfile())
  }

  @Test
  fun `principal of another type is ignored`() {
    SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("user", null, emptyList())

    assertNull(evaluator.currentProfile())
  }
}