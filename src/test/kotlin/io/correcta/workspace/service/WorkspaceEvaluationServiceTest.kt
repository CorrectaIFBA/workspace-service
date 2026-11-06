package io.correcta.workspace.service

import io.correcta.workspace.clients.AssessmentClient
import io.correcta.workspace.exceptions.ForbiddenException
import io.correcta.workspace.exceptions.NotFoundException
import io.correcta.workspace.messaging.evalution.EvaluationMessaging
import io.correcta.workspace.messaging.evalution.models.enums.EvaluationType
import io.correcta.workspace.messaging.evalution.models.requests.TriggerEvaluationRequest
import io.correcta.workspace.messaging.outbox.Outbox
import io.correcta.workspace.models.request.FinalEvaluationRequest
import io.correcta.workspace.models.response.clients.AssessmentSessionStatusResponse
import io.correcta.workspace.repositories.AssessmentQuestionDraftRevisionRepository
import io.correcta.workspace.repositories.AssessmentQuestionWorkspaceRepository
import io.correcta.workspace.service.Fixtures.ASSESSMENT_ID
import io.correcta.workspace.service.Fixtures.PROFILE_ID
import io.correcta.workspace.service.Fixtures.WORKSPACE_ID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WorkspaceEvaluationServiceTest {

  private val assessmentClient = mock(AssessmentClient::class.java)
  private val workspaceRepository = mock(AssessmentQuestionWorkspaceRepository::class.java)
  private val draftRepository = mock(AssessmentQuestionDraftRevisionRepository::class.java)
  private val outbox = mock(Outbox::class.java)

  private val service = WorkspaceEvaluationService(
    WorkspaceReader(workspaceRepository, draftRepository),
    SubmissionWindowGuard(assessmentClient),
    outbox,
  )

  @BeforeEach
  fun setUp() {
    `when`(assessmentClient.getSessionSubmissionStatus(ASSESSMENT_ID, PROFILE_ID))
      .thenReturn(AssessmentSessionStatusResponse("s1", "IN_PROGRESS", null, null, true, false))
  }

  @Test
  fun `partial run enqueues a PARTIAL evaluation for the latest draft`() {
    val workspace = Fixtures.workspace(revision = 2)
    val draft = Fixtures.draft(workspace, 2, sourceCode = "latest")
    `when`(workspaceRepository.findByIdAndStudentProfileId(WORKSPACE_ID, PROFILE_ID)).thenReturn(Optional.of(workspace))
    `when`(draftRepository.findByWorkspaceIdAndRevision(WORKSPACE_ID, 2)).thenReturn(Optional.of(draft))

    service.runPartialWorkspaceEvaluation(PROFILE_ID, WORKSPACE_ID)

    val payload = captureEnqueued()
    assertEquals(EvaluationType.PARTIAL, payload.type)
    assertEquals("latest", payload.sourceCode)
    assertEquals(WORKSPACE_ID, payload.workspaceId)
    assertNull(payload.submissionId)
  }

  @Test
  fun `partial run skips when the workspace has no draft`() {
    val workspace = Fixtures.workspace(revision = 0)
    `when`(workspaceRepository.findByIdAndStudentProfileId(WORKSPACE_ID, PROFILE_ID)).thenReturn(Optional.of(workspace))

    service.runPartialWorkspaceEvaluation(PROFILE_ID, WORKSPACE_ID)

    verify(outbox, never()).enqueue(anyString(), anyString(), anyArg())
  }

  @Test
  fun `partial run fails with 404 when the workspace is not the student's`() {
    `when`(workspaceRepository.findByIdAndStudentProfileId(WORKSPACE_ID, PROFILE_ID)).thenReturn(Optional.empty())

    assertFailsWith<NotFoundException> { service.runPartialWorkspaceEvaluation(PROFILE_ID, WORKSPACE_ID) }

    verify(outbox, never()).enqueue(anyString(), anyString(), anyArg())
  }

  @Test
  fun `partial run is forbidden when the session does not accept submissions`() {
    val workspace = Fixtures.workspace(revision = 1)
    `when`(workspaceRepository.findByIdAndStudentProfileId(WORKSPACE_ID, PROFILE_ID)).thenReturn(Optional.of(workspace))
    `when`(assessmentClient.getSessionSubmissionStatus(ASSESSMENT_ID, PROFILE_ID))
      .thenReturn(AssessmentSessionStatusResponse("s1", "ENDED", null, null, false, true))

    assertFailsWith<ForbiddenException> { service.runPartialWorkspaceEvaluation(PROFILE_ID, WORKSPACE_ID) }

    verify(outbox, never()).enqueue(anyString(), anyString(), anyArg())
  }

  @Test
  fun `trigger publishes the request payload on the evaluation exchange`() {
    val workspace = Fixtures.workspace(revision = 1)
    val submission = Fixtures.submission(workspace, Fixtures.draft(workspace, 1))

    service.triggerEvaluationOrchestrator(FinalEvaluationRequest(submission, workspace))

    verify(outbox).enqueue(
      EvaluationMessaging.EXCHANGE,
      EvaluationMessaging.TRIGGER_EVALUATION_KEY,
      FinalEvaluationRequest(submission, workspace).toPayload(),
    )
  }

  private fun captureEnqueued(): TriggerEvaluationRequest {
    val captor = ArgumentCaptor.forClass(Any::class.java)
    verify(outbox).enqueue(
      eqStr(EvaluationMessaging.EXCHANGE),
      eqStr(EvaluationMessaging.TRIGGER_EVALUATION_KEY),
      captor.capture() ?: Any(),
    )
    return captor.value as TriggerEvaluationRequest
  }

  private fun eqStr(value: String): String {
    org.mockito.ArgumentMatchers.eq(value)
    return value
  }
}