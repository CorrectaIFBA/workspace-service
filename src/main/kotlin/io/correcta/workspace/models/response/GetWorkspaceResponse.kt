package io.correcta.workspace.models.response

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionWorkspace

/** A workspace with its current draft, if any. */
data class GetWorkspaceResponse(
  val id: String,
  val entityVersion: Long?,
  val currentRevision: Int,
  val currentDraft: GetDraftRevisionResponse?,
  val createdAt: String,
) {

  companion object {

    /** Maps [workspace] and its current draft [revision] to a response. */
    fun fromWorkspace(workspace: AssessmentQuestionWorkspace, revision: AssessmentQuestionDraftRevision?): GetWorkspaceResponse = GetWorkspaceResponse(
      id = workspace.id!!,
      entityVersion = workspace.entityVersion,
      currentRevision = workspace.currentRevision,
      currentDraft = GetDraftRevisionResponse.fromDraftRevision(revision),
      createdAt = workspace.createdAt!!.toString(),
    )
  }
}