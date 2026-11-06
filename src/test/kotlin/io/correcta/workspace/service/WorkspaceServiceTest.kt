package io.correcta.workspace.service

import io.correcta.workspace.clients.AssessmentClient
import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionSubmission
import io.correcta.workspace.entities.AssessmentQuestionSubmissionStatus
import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.exceptions.BadRequestException
import io.correcta.workspace.exceptions.ForbiddenException
import io.correcta.workspace.exceptions.InternalServerErrorException
import io.correcta.workspace.exceptions.NotFoundException
import io.correcta.workspace.exceptions.PreconditionFailedException
import io.correcta.workspace.messaging.outbox.Outbox
import io.correcta.workspace.messaging.session.SessionDeliveredMessage
import io.correcta.workspace.messaging.session.SessionMessaging
import io.correcta.workspace.models.request.SaveDraftRevisionRequest
import io.correcta.workspace.models.response.clients.AssessmentSessionStatusResponse
import io.correcta.workspace.repositories.AssessmentQuestionDraftRevisionRepository
import io.correcta.workspace.repositories.AssessmentQuestionSubmissionRepository
import io.correcta.workspace.repositories.AssessmentQuestionWorkspaceRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceServiceTest {

  private val assessmentClient = mock(AssessmentClient::class.java)
  private val draftRepository = mock(AssessmentQuestionDraftRevisionRepository::class.java)
  private val submissionRepository = mock(AssessmentQuestionSubmissionRepository::class.java)
  private val workspaceRepository = mock(AssessmentQuestionWorkspaceRepository::class.java)
  private val transactionManager = mock(PlatformTransactionManager::class.java)
  private val outbox = mock(Outbox::class.java)
  private val clock = Clock.fixed(Instant.parse("2030-03-10T13:00:00Z"), ZoneOffset.UTC)

  private val reader = WorkspaceReader(workspaceRepository, draftRepository)
  private val guard = SubmissionWindowGuard(assessmentClient)

  private lateinit var service: WorkspaceService
  private lateinit var drafts: DraftService
  private lateinit var submissions: SubmissionService

  /** Everything submissions persisted through submissionRepository.save, in order. */
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

    `when`(draftRepository.save(anyK<AssessmentQuestionDraftRevision>())).thenAnswer {
      (it.arguments[0] as AssessmentQuestionDraftRevision).also { draft ->
        draft.id = "draft-${draft.revision}"
        draft.createdAt = Instant.now()
      }
    }

    service = WorkspaceService(workspaceRepository, reader, guard)
    drafts = DraftService(draftRepository, workspaceRepository, reader, guard)
    submissions = SubmissionService(
      submissionRepository,
      workspaceRepository,
      WorkspaceEvaluationService(reader, guard, outbox),
      reader,
      guard,
      TransactionTemplate(transactionManager),
      outbox,
      clock,
    )
  }

  // ---------------------------------------------------------------- createDraftForWorkspace

  @Test
  fun `createDraft saves revision 1 when If-Match is 0 and no draft exists`() {
    givenWorkspace(revision = 0)
    `when`(workspaceRepository.updateCurrentRevision(WORKSPACE_ID, 1, 0)).thenReturn(1)

    val response = drafts.createDraftForWorkspace(PROFILE_ID, WORKSPACE_ID, draftRequest(), "\"0\"")

    assertEquals(1, response.revision)
    verify(workspaceRepository).updateCurrentRevision(WORKSPACE_ID, 1, 0)
  }

  @Test
  fun `createDraft increments revision when If-Match matches current`() {
    givenWorkspace(revision = 4)
    `when`(workspaceRepository.updateCurrentRevision(WORKSPACE_ID, 5, 4)).thenReturn(1)

    val response = drafts.createDraftForWorkspace(PROFILE_ID, WORKSPACE_ID, draftRequest(), "\"4\"")

    assertEquals(5, response.revision)
  }

  @Test
  fun `createDraft fails with 412 when If-Match is stale and saves nothing`() {
    givenWorkspace(revision = 4)

    assertFailsWith<PreconditionFailedException> {
      drafts.createDraftForWorkspace(PROFILE_ID, WORKSPACE_ID, draftRequest(), "\"3\"")
    }

    verify(workspaceRepository, never()).updateCurrentRevision(anyStr(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())
    verify(draftRepository, never()).save(anyK<AssessmentQuestionDraftRevision>())
  }

  @Test
  fun `createDraft fails with 412 when compare-and-swap loses the race and saves no draft`() {
    givenWorkspace(revision = 4)
    // Another replica bumped the revision between our read and our update.
    `when`(workspaceRepository.updateCurrentRevision(WORKSPACE_ID, 5, 4)).thenReturn(0)

    assertFailsWith<PreconditionFailedException> {
      drafts.createDraftForWorkspace(PROFILE_ID, WORKSPACE_ID, draftRequest(), "\"4\"")
    }

    verify(draftRepository, never()).save(anyK<AssessmentQuestionDraftRevision>())
  }

  @ParameterizedTest
  @ValueSource(strings = ["3", "\"3", "3\"", "W/\"3\"", "\"007\"", "\"-1\"", "\"+3\"", "*", "\"1\",\"2\"", "", " ", "\"abc\"", "\"99999999999\""])
  fun `createDraft rejects malformed If-Match with 400`(ifMatch: String) {
    givenWorkspace(revision = 3)

    assertFailsWith<BadRequestException> {
      drafts.createDraftForWorkspace(PROFILE_ID, WORKSPACE_ID, draftRequest(), ifMatch)
    }

    verify(draftRepository, never()).save(anyK<AssessmentQuestionDraftRevision>())
  }

  @Test
  fun `createDraft fails with 404 when workspace does not belong to the student`() {
    `when`(workspaceRepository.findByIdAndStudentProfileId(WORKSPACE_ID, PROFILE_ID)).thenReturn(Optional.empty())

    assertFailsWith<NotFoundException> {
      drafts.createDraftForWorkspace(PROFILE_ID, WORKSPACE_ID, draftRequest(), "\"0\"")
    }
  }

  @Test
  fun `createDraft is forbidden when the session does not accept submissions`() {
    givenWorkspace(revision = 0)
    `when`(assessmentClient.getSessionSubmissionStatus(ASSESSMENT_ID, PROFILE_ID)).thenReturn(session(acceptsSubmissions = false))

    assertFailsWith<ForbiddenException> {
      drafts.createDraftForWorkspace(PROFILE_ID, WORKSPACE_ID, draftRequest(), "\"0\"")
    }

    verify(workspaceRepository, never()).updateCurrentRevision(anyStr(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())
  }

  @Test
  fun `createDraft is forbidden when the deadline has passed`() {
    givenWorkspace(revision = 0)
    `when`(assessmentClient.getSessionSubmissionStatus(ASSESSMENT_ID, PROFILE_ID)).thenReturn(session(acceptsSubmissions = false, late = true))

    assertFailsWith<ForbiddenException> {
      drafts.createDraftForWorkspace(PROFILE_ID, WORKSPACE_ID, draftRequest(), "\"0\"")
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

  // ---------------------------------------------------------------- getOrCreateWorkspaceForAssessmentQuestion

  @Test
  fun `getOrCreate returns the existing workspace without creating one`() {
    val existing = workspace(WORKSPACE_ID, revision = 0)
    `when`(workspaceRepository.findByStudentProfileIdAndAssessmentIdAndQuestionId(PROFILE_ID, ASSESSMENT_ID, QUESTION_ID))
      .thenReturn(Optional.of(existing))

    val response = service.getOrCreateWorkspaceForAssessmentQuestion(PROFILE_ID, ASSESSMENT_ID, QUESTION_ID)

    assertEquals(WORKSPACE_ID, response.id)
    assertNull(response.currentDraft)
    verify(workspaceRepository, never()).save(anyK<AssessmentQuestionWorkspace>())
    verify(draftRepository, never()).findByWorkspaceIdAndRevision(anyStr(), ArgumentMatchers.anyInt())
  }

  @Test
  fun `getOrCreate loads the current draft when the workspace has revisions`() {
    val existing = workspace(WORKSPACE_ID, revision = 3)
    `when`(workspaceRepository.findByStudentProfileIdAndAssessmentIdAndQuestionId(PROFILE_ID, ASSESSMENT_ID, QUESTION_ID))
      .thenReturn(Optional.of(existing))
    givenDraft(existing, 3)

    val response = service.getOrCreateWorkspaceForAssessmentQuestion(PROFILE_ID, ASSESSMENT_ID, QUESTION_ID)

    assertEquals(3, response.currentRevision)
    assertEquals(3, response.currentDraft?.revision)
  }

  @Test
  fun `getOrCreate creates the workspace when absent`() {
    `when`(workspaceRepository.findByStudentProfileIdAndAssessmentIdAndQuestionId(PROFILE_ID, ASSESSMENT_ID, QUESTION_ID))
      .thenReturn(Optional.empty())
    `when`(workspaceRepository.save(anyK<AssessmentQuestionWorkspace>())).thenAnswer {
      (it.arguments[0] as AssessmentQuestionWorkspace).also { ws ->
        ws.id = WORKSPACE_ID
        ws.createdAt = Instant.now()
      }
    }

    val response = service.getOrCreateWorkspaceForAssessmentQuestion(PROFILE_ID, ASSESSMENT_ID, QUESTION_ID)

    assertEquals(WORKSPACE_ID, response.id)
    assertEquals(0, response.currentRevision)
  }

  @Test
  fun `getOrCreate reuses the workspace created concurrently by another request`() {
    val winner = workspace(WORKSPACE_ID, revision = 0)
    `when`(workspaceRepository.findByStudentProfileIdAndAssessmentIdAndQuestionId(PROFILE_ID, ASSESSMENT_ID, QUESTION_ID))
      .thenReturn(Optional.empty(), Optional.of(winner))
    `when`(workspaceRepository.save(anyK<AssessmentQuestionWorkspace>())).thenThrow(DataIntegrityViolationException("duplicate"))

    val response = service.getOrCreateWorkspaceForAssessmentQuestion(PROFILE_ID, ASSESSMENT_ID, QUESTION_ID)

    assertEquals(WORKSPACE_ID, response.id)
  }

  // ---------------------------------------------------------------- helpers

  private fun session(acceptsSubmissions: Boolean = true, late: Boolean = false) = AssessmentSessionStatusResponse("s1", "IN_PROGRESS", null, null, acceptsSubmissions, late)

  private fun workspace(id: String, revision: Int) = AssessmentQuestionWorkspace(
    id = id,
    assessmentId = ASSESSMENT_ID,
    questionId = QUESTION_ID,
    studentProfileId = PROFILE_ID,
    entityVersion = 0,
    currentRevision = revision,
    createdAt = Instant.now(),
  )

  /** Stubs the lookups used when the workspace is addressed by id. */
  private fun givenWorkspace(revision: Int): AssessmentQuestionWorkspace {
    val workspace = workspace(WORKSPACE_ID, revision)
    `when`(workspaceRepository.findByIdAndStudentProfileId(WORKSPACE_ID, PROFILE_ID)).thenReturn(Optional.of(workspace))
    return workspace
  }

  private fun givenStudentWorkspaces(vararg workspaces: AssessmentQuestionWorkspace) {
    `when`(workspaceRepository.findByStudentProfileIdAndAssessmentId(PROFILE_ID, ASSESSMENT_ID)).thenReturn(workspaces.toList())
    workspaces.forEach { `when`(workspaceRepository.findById(it.id!!)).thenReturn(Optional.of(it)) }
  }

  private fun givenDraft(workspace: AssessmentQuestionWorkspace, revision: Int): AssessmentQuestionDraftRevision {
    val draft = AssessmentQuestionDraftRevision(
      id = "draft-${workspace.id}-$revision",
      workspace = workspace,
      revision = revision,
      sourceCode = "int main() { return 0; }",
      language = "C",
      createdAt = Instant.now(),
    )
    `when`(draftRepository.findByWorkspaceIdAndRevision(workspace.id!!, revision)).thenReturn(Optional.of(draft))
    return draft
  }

  private fun submission(
    workspace: AssessmentQuestionWorkspace,
    number: Int,
    revision: Int,
    status: AssessmentQuestionSubmissionStatus,
  ) = AssessmentQuestionSubmission(
    id = "sub-${workspace.id}-$number",
    workspace = workspace,
    submissionNumber = number,
    status = status,
    draftRevision = AssessmentQuestionDraftRevision(
      id = "draft-${workspace.id}-$revision",
      workspace = workspace,
      revision = revision,
      sourceCode = "x",
      language = "C",
    ),
  )

  private fun draftRequest() = SaveDraftRevisionRequest(sourceCode = "int main() {}", language = "C")

  /** Mockito matcher usable for non-null Kotlin parameters. */
  @Suppress("UNCHECKED_CAST")
  private fun <T> anyK(): T {
    ArgumentMatchers.any<T>()
    return null as T
  }

  private fun anyStr(): String {
    ArgumentMatchers.anyString()
    return ""
  }

  companion object {
    private const val PROFILE_ID = "p1"
    private const val ASSESSMENT_ID = "a1"
    private const val QUESTION_ID = "q1"
    private const val WORKSPACE_ID = "w-main"
  }
}