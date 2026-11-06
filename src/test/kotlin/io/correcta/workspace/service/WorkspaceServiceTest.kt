package io.correcta.workspace.service

import io.correcta.workspace.clients.AssessmentClient
import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.messaging.outbox.Outbox
import io.correcta.workspace.repositories.AssessmentQuestionDraftRevisionRepository
import io.correcta.workspace.repositories.AssessmentQuestionSubmissionRepository
import io.correcta.workspace.repositories.AssessmentQuestionWorkspaceRepository
import io.correcta.workspace.service.Fixtures.ASSESSMENT_ID
import io.correcta.workspace.service.Fixtures.PROFILE_ID
import io.correcta.workspace.service.Fixtures.QUESTION_ID
import io.correcta.workspace.service.Fixtures.WORKSPACE_ID
import io.correcta.workspace.service.Fixtures.draft
import io.correcta.workspace.service.Fixtures.session
import io.correcta.workspace.service.Fixtures.workspace
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
  private val service = WorkspaceService(workspaceRepository, reader, guard)

  @BeforeEach
  fun setUp() {
    `when`(assessmentClient.getSessionSubmissionStatus(ASSESSMENT_ID, PROFILE_ID)).thenReturn(session())
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

  private fun givenDraft(workspace: AssessmentQuestionWorkspace, revision: Int): AssessmentQuestionDraftRevision {
    val draft = draft(workspace, revision)
    `when`(draftRepository.findByWorkspaceIdAndRevision(workspace.id!!, revision)).thenReturn(Optional.of(draft))
    return draft
  }
}