package io.correcta.workspace.models.response

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionWorkspace

data class GetWorkspaceResponse(
    val id: String,
    val entityVersion: Long? = null,
    val currentRevision: Int = 0,
    val currentDraft: GetDraftRevisionResponse? = null,
    val createdAt: String,
) {

    companion object {

        fun fromWorkspace(workspace: AssessmentQuestionWorkspace, revision: AssessmentQuestionDraftRevision?): GetWorkspaceResponse {
            return GetWorkspaceResponse(
                id = workspace.id!!,
                entityVersion = workspace.entityVersion,
                currentRevision = workspace.currentRevision,
                currentDraft = GetDraftRevisionResponse.fromDraftRevision(revision),
                createdAt = workspace.createdAt!!.toString()
            )
        }
    }
}
