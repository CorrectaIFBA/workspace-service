package io.correcta.workspace.models.response

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision

/** A draft revision with its source code. */
data class GetDraftRevisionResponse(
  val id: String,
  val workspaceId: String,
  val revision: Int,
  val language: String,
  val sourceCode: String,
  val createdAt: String,
) {

  companion object {

    /** Maps [revision] to a response, or returns null when there is no draft. */
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