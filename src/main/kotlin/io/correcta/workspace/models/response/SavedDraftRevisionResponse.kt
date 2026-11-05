package io.correcta.workspace.models.response

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision

data class SavedDraftRevisionResponse(
    val id: String,
    val workspaceId: String,
    val revision: Int,
    val createdAt: String,
) {

    companion object {

        fun fromDraftRevision(revision: AssessmentQuestionDraftRevision): SavedDraftRevisionResponse {

            return SavedDraftRevisionResponse(
                id = revision.id!!,
                workspaceId = revision.workspace.id!!,
                revision = revision.revision,
                createdAt = revision.createdAt!!.toString()
            )
        }
    }
}
