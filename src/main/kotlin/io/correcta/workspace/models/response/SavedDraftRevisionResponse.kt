package io.correcta.workspace.models.response

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision

/** Result of saving a draft: the new revision, without the source code. */
data class SavedDraftRevisionResponse(
  val id: String,
  val workspaceId: String,
  val revision: Int,
  val createdAt: String,
) {

  companion object {

    /** Maps a saved [revision] to a response. */
    fun fromDraftRevision(revision: AssessmentQuestionDraftRevision): SavedDraftRevisionResponse = SavedDraftRevisionResponse(
      id = revision.id!!,
      workspaceId = revision.workspace.id!!,
      revision = revision.revision,
      createdAt = revision.createdAt!!.toString(),
    )
  }
}