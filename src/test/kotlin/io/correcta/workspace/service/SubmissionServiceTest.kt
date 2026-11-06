package io.correcta.workspace.service

import io.correcta.workspace.clients.AssessmentClient
import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionSubmission
import io.correcta.workspace.entities.AssessmentQuestionSubmissionStatus
import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.exceptions.ForbiddenException
import io.correcta.workspace.exceptions.InternalServerErrorException
import io.correcta.workspace.messaging.outbox.Outbox
import io.correcta.workspace.messaging.session.SessionDeliveredMessage
import io.correcta.workspace.messaging.session.SessionMessaging
import io.correcta.workspace.repositories.AssessmentQuestionDraftRevisionRepository
import io.correcta.workspace.repositories.AssessmentQuestionSubmissionRepository
import io.correcta.workspace.repositories.AssessmentQuestionWorkspaceRepository
import io.correcta.workspace.service.Fixtures.ASSESSMENT_ID
import io.correcta.workspace.service.Fixtures.PROFILE_ID
import io.correcta.workspace.service.Fixtures.WORKSPACE_ID
import io.correcta.workspace.service.Fixtures.draft
import io.correcta.workspace.service.Fixtures.session
import io.correcta.workspace.service.Fixtures.workspace
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SubmissionServiceTest {

  private val assessmentClient = mock(AssessmentClient::class.java)
  private val draftRepository = mock(AssessmentQuestionDraftRevisionRepository::class.java)
  private val submissionRepository = mock(AssessmentQuestionSubmissionRepository::class.java)
  private val workspaceRepository = mock(AssessmentQuestionWorkspaceRepository::class.java)
  private val transactionManager = mock(PlatformTransactionManager::class.java)
  private val outbox = mock(Outbox::class.java)
  private val clock = Clock.fixed(Instant.parse("2030-03-10T13:00:00Z"), ZoneOffset.UTC)

  private val reader = WorkspaceReader(workspaceRepository, draftRepository)
  private val guard = SubmissionWindowGuard(assessmentClient)
  private val submissions = SubmissionService(
    submissionRepository,
    workspaceRepository,
    WorkspaceEvaluationService(reader, guard, outbox),
    reader,
    guard,
    TransactionTemplate(transactionManager),
    outbox,
    clock,
  )

  /** Everything persisted through submissionRepository.save, in order. */
  private val savedSubmissions = mutableListOf<AssessmentQuestionSubmission>()

  @BeforeEach
  fun setUp() {
    `when`(transactionManager.getTransaction(anyK())).thenReturn(SimpleTransactionStatus())
    `when`(assessmentClient.getSessionSubmissionStatus(ASSESSMENT_ID, PROFILE_ID)).thenReturn(session())
    `when`(submissionRepository.save(anyK<AssessmentQuestionSubmission>())).thenAnswer {
      val submission = it.arguments[0] as AssessmentQuestionSubmission
      savedSubmissions.add(submission)
      submission
    }
  }

  // ---------------------------------------------------------------- submission numbering (shared by every submit)

  @Test
  fun `submit creates submission number 1 for the first submission`() {
    val workspace = workspace(WORKSPACE_ID, revision = 2)
    givenStudentWorkspaces(workspace)
    givenDraft(workspace, 2)

    submissions.submitAllWorkspacesForStudent(ASSESSMENT_ID, PROFILE_ID)

    assertEquals(1, savedSubmissions.size)
    assertEquals(1, savedSubmissions[0].submissionNumber)
    assertEquals(2, savedSubmissions[0].draftRevision.revision)
    assertEquals(AssessmentQuestionSubmissionStatus.ACTIVE, savedSubmissions[0].status)
  }

  @Test
  fun `submit supersedes the previous ACTIVE submission and increments the number`() {
    val workspace = workspace(WORKSPACE_ID, revision = 3)
    givenStudentWorkspaces(workspace)
    givenDraft(workspace, 3)
    val previous = submission(workspace, number = 1, revision = 2, status = AssessmentQuestionSubmissionStatus.ACTIVE)
    `when`(submissionRepository.findByWorkspaceIdOrderBySubmissionNumberDesc(WORKSPACE_ID)).thenReturn(listOf(previous))

    submissions.submitAllWorkspacesForStudent(ASSESSMENT_ID, PROFILE_ID)

    assertEquals(AssessmentQuestionSubmissionStatus.SUPERSEDED, previous.status)
    assertEquals(1, savedSubmissions.size)
    assertEquals(2, savedSubmissions[0].submissionNumber)
    assertEquals(AssessmentQuestionSubmissionStatus.ACTIVE, savedSubmissions[0].status)
  }

  @Test
  fun `submit keeps already SUPERSEDED submissions untouched and numbers after the latest`() {
    val workspace = workspace(WORKSPACE_ID, revision = 5)
    givenStudentWorkspaces(workspace)
    givenDraft(workspace, 5)
    val active = submission(workspace, number = 2, revision = 4, status = AssessmentQuestionSubmissionStatus.ACTIVE)
    val old = submission(workspace, number = 1, revision = 1, status = AssessmentQuestionSubmissionStatus.SUPERSEDED)
    `when`(submissionRepository.findByWorkspaceIdOrderBySubmissionNumberDesc(WORKSPACE_ID)).thenReturn(listOf(active, old))

    submissions.submitAllWorkspacesForStudent(ASSESSMENT_ID, PROFILE_ID)

    assertEquals(AssessmentQuestionSubmissionStatus.SUPERSEDED, active.status)
    assertEquals(AssessmentQuestionSubmissionStatus.SUPERSEDED, old.status)
    assertEquals(3, savedSubmissions.single().submissionNumber)
  }

  @Test
  fun `submit is idempotent when the ACTIVE submission already has the same draft revision`() {
    val workspace = workspace(WORKSPACE_ID, revision = 3)
    givenStudentWorkspaces(workspace)
    givenDraft(workspace, 3)
    val active = submission(workspace, number = 1, revision = 3, status = AssessmentQuestionSubmissionStatus.ACTIVE)
    `when`(submissionRepository.findByWorkspaceIdOrderBySubmissionNumberDesc(WORKSPACE_ID)).thenReturn(listOf(active))

    submissions.submitAllWorkspacesForStudent(ASSESSMENT_ID, PROFILE_ID)

    assertTrue(savedSubmissions.isEmpty())
    assertEquals(AssessmentQuestionSubmissionStatus.ACTIVE, active.status)
  }

  // ---------------------------------------------------------------- submit all

  @Test
  fun `submitAll submits the latest draft of each workspace and skips workspaces without drafts`() {
    val w1 = workspace("w1", revision = 1)
    val w2 = workspace("w2", revision = 0)
    val w3 = workspace("w3", revision = 2)
    givenStudentWorkspaces(w1, w2, w3)
    givenDraft(w1, 1)
    givenDraft(w3, 2)

    submissions.submitAllWorkspacesForAssessment(PROFILE_ID, ASSESSMENT_ID)

    assertEquals(listOf("w1", "w3"), savedSubmissions.map { it.workspace.id })
    assertEquals(listOf(1, 2), savedSubmissions.map { it.draftRevision.revision })
    verify(outbox).enqueue(
      SessionMessaging.EXCHANGE,
      SessionMessaging.SESSION_DELIVERED_KEY,
      SessionDeliveredMessage(ASSESSMENT_ID, PROFILE_ID, "2030-03-10T13:00:00Z"),
    )
  }

  @Test
  fun `submitAll skips workspaces whose ACTIVE submission already matches the latest draft`() {
    val w1 = workspace("w1", revision = 1)
    val w2 = workspace("w2", revision = 2)
    givenStudentWorkspaces(w1, w2)
    givenDraft(w1, 1)
    givenDraft(w2, 2)
    `when`(submissionRepository.findByWorkspaceIdOrderBySubmissionNumberDesc("w1"))
      .thenReturn(listOf(submission(w1, number = 1, revision = 1, status = AssessmentQuestionSubmissionStatus.ACTIVE)))

    submissions.submitAllWorkspacesForAssessment(PROFILE_ID, ASSESSMENT_ID)

    assertEquals(listOf("w2"), savedSubmissions.map { it.workspace.id })
  }

  @Test
  fun `submitAll keeps going when one workspace fails and then reports the failure`() {
    val w1 = workspace("w1", revision = 1)
    val w2 = workspace("w2", revision = 1)
    givenStudentWorkspaces(w1, w2)
    `when`(draftRepository.findByWorkspaceIdAndRevision("w1", 1)).thenThrow(RuntimeException("boom"))
    givenDraft(w2, 1)

    assertFailsWith<InternalServerErrorException> { submissions.submitAllWorkspacesForAssessment(PROFILE_ID, ASSESSMENT_ID) }

    assertEquals(listOf("w2"), savedSubmissions.map { it.workspace.id })
    // An answer is missing: the session must not be delivered.
    verify(outbox, never()).enqueue(eq(SessionMessaging.EXCHANGE) ?: SessionMessaging.EXCHANGE, anyStr(), anyK())
  }

  @Test
  fun `submitAll is forbidden after the deadline and touches no workspace`() {
    `when`(assessmentClient.getSessionSubmissionStatus(ASSESSMENT_ID, PROFILE_ID)).thenReturn(session(acceptsSubmissions = false, late = true))

    assertFailsWith<ForbiddenException> { submissions.submitAllWorkspacesForAssessment(PROFILE_ID, ASSESSMENT_ID) }

    verify(workspaceRepository, never()).findByStudentProfileIdAndAssessmentId(anyStr(), anyStr())
    verify(outbox, never()).enqueue(anyStr(), anyStr(), anyK())
  }

  @Test
  fun `submitAllForStudent ignores the deadline`() {
    val w1 = workspace("w1", revision = 1)
    givenStudentWorkspaces(w1)
    givenDraft(w1, 1)
    `when`(assessmentClient.getSessionSubmissionStatus(ASSESSMENT_ID, PROFILE_ID)).thenReturn(session(acceptsSubmissions = false, late = true))

    submissions.submitAllWorkspacesForStudent(ASSESSMENT_ID, PROFILE_ID)

    assertEquals(listOf("w1"), savedSubmissions.map { it.workspace.id })
    verify(assessmentClient, never()).getSessionSubmissionStatus(anyStr(), anyStr())
    // Called when the session already ended: nothing to announce back.
    verify(outbox, never()).enqueue(eq(SessionMessaging.EXCHANGE) ?: SessionMessaging.EXCHANGE, anyStr(), anyK())
  }

  @Test
  fun `submitAll delivers again on retry, with nothing left to submit`() {
    val w1 = workspace("w1", revision = 1)
    givenStudentWorkspaces(w1)
    givenDraft(w1, 1)
    `when`(submissionRepository.findByWorkspaceIdOrderBySubmissionNumberDesc("w1"))
      .thenReturn(listOf(submission(w1, number = 1, revision = 1, status = AssessmentQuestionSubmissionStatus.ACTIVE)))

    submissions.submitAllWorkspacesForAssessment(PROFILE_ID, ASSESSMENT_ID)
    submissions.submitAllWorkspacesForAssessment(PROFILE_ID, ASSESSMENT_ID)

    assertTrue(savedSubmissions.isEmpty())
    verify(outbox, times(2)).enqueue(anyStr(), anyStr(), anyK())
  }

  // ---------------------------------------------------------------- helpers

  private fun givenStudentWorkspaces(vararg workspaces: AssessmentQuestionWorkspace) {
    `when`(workspaceRepository.findByStudentProfileIdAndAssessmentId(PROFILE_ID, ASSESSMENT_ID)).thenReturn(workspaces.toList())
    workspaces.forEach { `when`(workspaceRepository.findById(it.id!!)).thenReturn(Optional.of(it)) }
  }

  private fun givenDraft(workspace: AssessmentQuestionWorkspace, revision: Int): AssessmentQuestionDraftRevision {
    val draft = draft(workspace, revision)
    `when`(draftRepository.findByWorkspaceIdAndRevision(workspace.id!!, revision)).thenReturn(Optional.of(draft))
    return draft
  }

  private fun submission(
    workspace: AssessmentQuestionWorkspace,
    number: Int,
    revision: Int,
    status: AssessmentQuestionSubmissionStatus,
  ) = Fixtures.submission(workspace, draft(workspace, revision, sourceCode = "x"), number, status)
}