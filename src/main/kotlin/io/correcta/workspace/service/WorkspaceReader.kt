package io.correcta.workspace.service

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.exceptions.NotFoundException
import io.correcta.workspace.repositories.AssessmentQuestionDraftRevisionRepository
import io.correcta.workspace.repositories.AssessmentQuestionWorkspaceRepository
import org.springframework.stereotype.Component

@Component
class WorkspaceReader(
  val workspaceRepository: AssessmentQuestionWorkspaceRepository,
  val draftRepository: AssessmentQuestionDraftRevisionRepository,
) {

  fun getOwned(workspaceId: String, profileId: String): AssessmentQuestionWorkspace = workspaceRepository.findByIdAndStudentProfileId(
    id = workspaceId,
    studentProfileId = profileId,
  ).orElseThrow {
    NotFoundException("Não foi possível encontrar o workspace $workspaceId para o estudante $profileId.")
  }

  fun latestDraft(workspace: AssessmentQuestionWorkspace): AssessmentQuestionDraftRevision? {
    if (workspace.currentRevision == 0) return null

    return draftRepository
      .findByWorkspaceIdAndRevision(workspace.id!!, workspace.currentRevision)
      .orElse(null)
  }
}