package io.correcta.workspace.controllers.impl

import io.correcta.workspace.controllers.WorkspaceController
import io.correcta.workspace.models.request.SaveDraftRevisionRequest
import io.correcta.workspace.models.response.GetWorkspaceResponse
import io.correcta.workspace.models.response.SavedDraftRevisionResponse
import io.correcta.workspace.service.WorkspaceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class WorkspaceControllerImpl(
    val workspaceService: WorkspaceService
) : WorkspaceController {

    override fun getWorkspaceForAssessmentQuestion(
        assessmentId: String,
        questionId: String
    ): ResponseEntity<GetWorkspaceResponse> {
        val workspace = workspaceService.getOrCreateWorkspaceForAssessmentQuestion(assessmentId, questionId)

        return ResponseEntity.ok().eTag("\"${workspace.currentRevision}\"").body(workspace)
    }

    override fun createDraftForWorkspace(workspaceId: String, body: SaveDraftRevisionRequest, ifMatch: String): ResponseEntity<SavedDraftRevisionResponse> {
        val saved = workspaceService.createDraftForWorkspace(workspaceId, body, ifMatch)

        return ResponseEntity.ok().eTag("\"${saved.revision}\"").body(saved)
    }

    override fun runWorkspace(workspaceId: String, ifMatch: String): GetWorkspaceResponse {
        TODO("Not yet implemented")
    }

    override fun submitAllWorkspacesForAssessment(assessmentId: String) {
        workspaceService.submitAllWorkspacesForAssessment(assessmentId)
    }
}
