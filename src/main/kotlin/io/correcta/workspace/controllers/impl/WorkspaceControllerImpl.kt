package io.correcta.workspace.controllers.impl

import io.correcta.workspace.controllers.WorkspaceController
import io.correcta.workspace.exceptions.InternalServerErrorException
import io.correcta.workspace.models.request.SaveDraftRevisionRequest
import io.correcta.workspace.models.response.GetWorkspaceResponse
import io.correcta.workspace.models.response.SavedDraftRevisionResponse
import io.correcta.workspace.security.components.ScopeEvaluatorComponent
import io.correcta.workspace.service.DraftService
import io.correcta.workspace.service.SubmissionService
import io.correcta.workspace.service.WorkspaceEvaluationService
import io.correcta.workspace.service.WorkspaceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * Thin HTTP layer: resolves the authenticated profile, delegates to the services and sets the ETag
 * (the quoted revision number) that clients send back in `If-Match`.
 */
@RestController
class WorkspaceControllerImpl(
  val workspaceService: WorkspaceService,
  val draftService: DraftService,
  val submissionService: SubmissionService,
  val workspaceEvaluationService: WorkspaceEvaluationService,
  val scopeEvaluator: ScopeEvaluatorComponent,
) : WorkspaceController {

  override fun getWorkspaceForAssessmentQuestion(
    assessmentId: String,
    questionId: String,
  ): ResponseEntity<GetWorkspaceResponse> {
    val workspace = workspaceService.getOrCreateWorkspaceForAssessmentQuestion(profileId(), assessmentId, questionId)

    return ResponseEntity.ok().eTag("\"${workspace.currentRevision}\"").body(workspace)
  }

  override fun createDraftForWorkspace(workspaceId: String, body: SaveDraftRevisionRequest, ifMatch: String): ResponseEntity<SavedDraftRevisionResponse> {
    val saved = draftService.createDraftForWorkspace(profileId(), workspaceId, body, ifMatch)

    return ResponseEntity.ok().eTag("\"${saved.revision}\"").body(saved)
  }

  override fun runWorkspace(workspaceId: String) {
    workspaceEvaluationService.runPartialWorkspaceEvaluation(profileId(), workspaceId)
  }

  override fun submitAllWorkspacesForAssessment(assessmentId: String) {
    submissionService.submitAllWorkspacesForAssessment(profileId(), assessmentId)
  }

  private fun profileId(): String {
    val profile = scopeEvaluator.currentProfile()
      ?: throw InternalServerErrorException("Falha ao recuperar o principal da autenticação")

    return profile.profileId
  }
}