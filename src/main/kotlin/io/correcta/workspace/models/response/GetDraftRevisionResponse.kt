package io.correcta.workspace.models.response

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision

data class GetDraftRevisionResponse(
  val id: String,
  val workspaceId: String,
  val revision: Int,
  val language: String,
  val sourceCode: String,
  val createdAt: String,
) {

  companion object {

    fun fromDraftRevision(revision: AssessmentQuestionDraftRevision?): GetDraftRevisionResponse? {
      if (revision == null) return null

      return GetDraftRevisionResponse(
        id = revision.id!!,
        workspaceId = revision.workspace.id!!,
        revision = revision.revision,
        language = revision.language,
        sourceCode = revision.sourceCode,
        createdAt = revision.createdAt!!.toString(),
      )
    }
  }
}