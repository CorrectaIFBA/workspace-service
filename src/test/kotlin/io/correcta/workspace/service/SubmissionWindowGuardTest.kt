package io.correcta.workspace.service

import io.correcta.workspace.clients.AssessmentClient
import io.correcta.workspace.exceptions.ForbiddenException
import io.correcta.workspace.models.response.clients.AssessmentSessionStatusResponse
import io.correcta.workspace.service.Fixtures.ASSESSMENT_ID
import io.correcta.workspace.service.Fixtures.PROFILE_ID
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class SubmissionWindowGuardTest {

  private val client = mock(AssessmentClient::class.java)
  private val guard = SubmissionWindowGuard(client)

  private fun givenStatus(acceptsSubmissions: Boolean, late: Boolean) {
    `when`(client.getSessionSubmissionStatus(ASSESSMENT_ID, PROFILE_ID))
      .thenReturn(AssessmentSessionStatusResponse("s1", "IN_PROGRESS", null, null, acceptsSubmissions, late))
  }

  @Test
  fun `passes when session accepts submissions`() {
    givenStatus(acceptsSubmissions = true, late = false)

    guard.assertAcceptsSubmissions(ASSESSMENT_ID, PROFILE_ID)
  }

  @Test
  fun `forbids with deadline message when session is closed and late`() {
    givenStatus(acceptsSubmissions = false, late = true)

    val e = assertFailsWith<ForbiddenException> { guard.assertAcceptsSubmissions(ASSESSMENT_ID, PROFILE_ID) }

    assertContains(e.message!!, "prazo")
  }

  @Test
  fun `forbids with generic message when session is closed but not late`() {
    givenStatus(acceptsSubmissions = false, late = false)

    val e = assertFailsWith<ForbiddenException> { guard.assertAcceptsSubmissions(ASSESSMENT_ID, PROFILE_ID) }

    assertContains(e.message!!, "não pode submeter")
  }

  @Test
  fun `passes when session accepts submissions even if late flag is set`() {
    givenStatus(acceptsSubmissions = true, late = true)

    guard.assertAcceptsSubmissions(ASSESSMENT_ID, PROFILE_ID)
  }
}