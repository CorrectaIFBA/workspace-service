package io.correcta.workspace.service

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.exceptions.NotFoundException
import io.correcta.workspace.repositories.AssessmentQuestionDraftRevisionRepository
import io.correcta.workspace.repositories.AssessmentQuestionWorkspaceRepository
import org.springframework.stereotype.Component

/** Shared read helpers for workspaces and their drafts. */
@Component
class WorkspaceReader(
  val workspaceRepository: AssessmentQuestionWorkspaceRepository,
  val draftRepository: AssessmentQuestionDraftRevisionRepository,
) {

  /** Loads a workspace only if it belongs to [profileId]; someone else's workspace looks the same as a missing one (404). */
  fun getOwned(workspaceId: String, profileId: String): AssessmentQuestionWorkspace = workspaceRepository.findByIdAndStudentProfileId(
    id = workspaceId,
    studentProfileId = profileId,
  ).orElseThrow {
    NotFoundException("Não foi possível encontrar o workspace $workspaceId para o estudante $profileId.")
  }

  /** Draft at the workspace's current revision, or null when the student never saved (revision 0). */
  fun latestDraft(workspace: AssessmentQuestionWorkspace): AssessmentQuestionDraftRevision? {
    if (workspace.currentRevision == 0) return null

    return draftRepository
      .findByWorkspaceIdAndRevision(workspace.id!!, workspace.currentRevision)
      .orElse(null)
  }
}