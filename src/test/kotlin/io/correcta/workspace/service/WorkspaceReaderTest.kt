package io.correcta.workspace.service

import io.correcta.workspace.exceptions.NotFoundException
import io.correcta.workspace.repositories.AssessmentQuestionDraftRevisionRepository
import io.correcta.workspace.repositories.AssessmentQuestionWorkspaceRepository
import io.correcta.workspace.service.Fixtures.PROFILE_ID
import io.correcta.workspace.service.Fixtures.WORKSPACE_ID
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class WorkspaceReaderTest {

  private val workspaceRepository = mock(AssessmentQuestionWorkspaceRepository::class.java)
  private val draftRepository = mock(AssessmentQuestionDraftRevisionRepository::class.java)
  private val reader = WorkspaceReader(workspaceRepository, draftRepository)

  @Test
  fun `getOwned returns the workspace of the student`() {
    val workspace = Fixtures.workspace()
    `when`(workspaceRepository.findByIdAndStudentProfileId(WORKSPACE_ID, PROFILE_ID)).thenReturn(Optional.of(workspace))

    assertSame(workspace, reader.getOwned(WORKSPACE_ID, PROFILE_ID))
  }

  @Test
  fun `getOwned throws NotFound when the workspace is missing or owned by someone else`() {
    `when`(workspaceRepository.findByIdAndStudentProfileId(WORKSPACE_ID, PROFILE_ID)).thenReturn(Optional.empty())

    val e = assertFailsWith<NotFoundException> { reader.getOwned(WORKSPACE_ID, PROFILE_ID) }

    assertEquals(
      "Não foi possível encontrar o workspace $WORKSPACE_ID para o estudante $PROFILE_ID.",
      e.message,
    )
  }

  @Test
  fun `latestDraft is null at revision 0 and never queries drafts`() {
    assertNull(reader.latestDraft(Fixtures.workspace(revision = 0)))

    verify(draftRepository, never()).findByWorkspaceIdAndRevision(anyString(), anyInt())
  }

  @Test
  fun `latestDraft returns the draft at the current revision`() {
    val workspace = Fixtures.workspace(revision = 3)
    val draft = Fixtures.draft(workspace, 3)
    `when`(draftRepository.findByWorkspaceIdAndRevision(WORKSPACE_ID, 3)).thenReturn(Optional.of(draft))

    assertSame(draft, reader.latestDraft(workspace))
  }

  @Test
  fun `latestDraft is null when the draft row is missing`() {
    val workspace = Fixtures.workspace(revision = 2)
    `when`(draftRepository.findByWorkspaceIdAndRevision(WORKSPACE_ID, 2)).thenReturn(Optional.empty())

    assertNull(reader.latestDraft(workspace))
  }
}