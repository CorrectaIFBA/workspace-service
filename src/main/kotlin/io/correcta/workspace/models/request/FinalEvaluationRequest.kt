package io.correcta.workspace.models.request

import io.correcta.workspace.entities.AssessmentQuestionSubmission
import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.messaging.evalution.models.enums.EvaluationType
import io.correcta.workspace.messaging.evalution.models.requests.TriggerEvaluationRequest

/** Official evaluation of a submission; the result is tied to [submission]. */
data class FinalEvaluationRequest(
  val submission: AssessmentQuestionSubmission,
  val workspace: AssessmentQuestionWorkspace,
) : EvaluationRequest {

  override fun toPayload() = TriggerEvaluationRequest(
    type = EvaluationType.FINAL,

    sourceCode = submission.draftRevision.sourceCode,
    language = submission.draftRevision.language,

    submissionId = submission.id,
    workspaceId = workspace.id!!,
    assessmentId = workspace.assessmentId,
    questionId = workspace.questionId,
    studentProfileId = workspace.studentProfileId,
  )
}