package io.correcta.workspace.models

import io.correcta.workspace.messaging.evalution.models.enums.EvaluationType
import io.correcta.workspace.models.request.FinalEvaluationRequest
import io.correcta.workspace.models.request.PartialEvaluationRequest
import io.correcta.workspace.service.Fixtures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EvaluationRequestTest {

  private val workspace = Fixtures.workspace(revision = 2)
  private val draft = Fixtures.draft(workspace, 2, sourceCode = "print(1)")

  @Test
  fun `final request maps submission and workspace to a FINAL payload`() {
    val submission = Fixtures.submission(workspace, draft, number = 4)

    val payload = FinalEvaluationRequest(submission, workspace).toPayload()

    assertEquals(EvaluationType.FINAL, payload.type)
    assertEquals(submission.id, payload.submissionId)
    assertEquals("print(1)", payload.sourceCode)
    assertEquals("C", payload.language)
    assertEquals(workspace.id, payload.workspaceId)
    assertEquals(workspace.assessmentId, payload.assessmentId)
    assertEquals(workspace.questionId, payload.questionId)
    assertEquals(workspace.studentProfileId, payload.studentProfileId)
  }

  @Test
  fun `partial request maps draft and workspace to a PARTIAL payload without submission id`() {
    val payload = PartialEvaluationRequest(draft, workspace).toPayload()

    assertEquals(EvaluationType.PARTIAL, payload.type)
    assertNull(payload.submissionId)
    assertEquals("print(1)", payload.sourceCode)
    assertEquals("C", payload.language)
    assertEquals(workspace.id, payload.workspaceId)
    assertEquals(workspace.assessmentId, payload.assessmentId)
    assertEquals(workspace.questionId, payload.questionId)
    assertEquals(workspace.studentProfileId, payload.studentProfileId)
  }
}