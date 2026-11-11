package io.correcta.workspace.service

import io.correcta.workspace.clients.AssessmentClient
import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.exceptions.BadRequestException
import io.correcta.workspace.exceptions.ForbiddenException
import io.correcta.workspace.exceptions.NotFoundException
import io.correcta.workspace.exceptions.PreconditionFailedException
import io.correcta.workspace.models.request.SaveDraftRevisionRequest
import io.correcta.workspace.repositories.AssessmentQuestionDraftRevisionRepository
import io.correcta.workspace.repositories.AssessmentQuestionWorkspaceRepository
import io.correcta.workspace.service.Fixtures.ASSESSMENT_ID
import io.correcta.workspace.service.Fixtures.PROFILE_ID
import io.correcta.workspace.service.Fixtures.WORKSPACE_ID
import io.correcta.workspace.service.Fixtures.session
import io.correcta.workspace.service.Fixtures.workspace
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DraftServiceTest {

  private val assessmentClient = mock(AssessmentClient::class.java)
  private val draftRepository = mock(AssessmentQuestionDraftRevisionRepository::class.java)
  private val workspaceRepository = mock(AssessmentQuestionWorkspaceRepository::class.java)

  private val reader = WorkspaceReader(workspaceRepository, draftRepository)
  private val guard = SubmissionWindowGuard(assessmentClient)
  private val drafts = DraftService(draftRepository, workspaceRepository, reader, guard)

  @BeforeEach
  fun setUp() {
    `when`(assessmentClient.getSessionSubmissionStatus(ASSESSMENT_ID, PROFILE_ID)).thenReturn(session())
    `when`(draftRepository.save(anyK<AssessmentQuestionDraftRevision>())).thenAnswer {
      (it.arguments[0] as AssessmentQuestionDraftRevision).also { draft ->
        draft.id = "draft-${draft.revision}"
        draft.createdAt = Instant.now()
      }
    }
  }

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

  /** Stubs the lookups used when the workspace is addressed by id. */
  private fun givenWorkspace(revision: Int): AssessmentQuestionWorkspace {
    val workspace = workspace(WORKSPACE_ID, revision)
    `when`(workspaceRepository.findByIdAndStudentProfileId(WORKSPACE_ID, PROFILE_ID)).thenReturn(Optional.of(workspace))
    return workspace
  }

  private fun draftRequest() = SaveDraftRevisionRequest(sourceCode = "int main() {}", language = "C")
}