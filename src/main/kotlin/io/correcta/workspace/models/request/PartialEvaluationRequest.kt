package io.correcta.workspace.models.request

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.messaging.evalution.models.enums.EvaluationType
import io.correcta.workspace.messaging.evalution.models.requests.TriggerEvaluationRequest

data class PartialEvaluationRequest(
    val draftRevision: AssessmentQuestionDraftRevision,
    val workspace: AssessmentQuestionWorkspace
) : EvaluationRequest {

    override fun toPayload() = TriggerEvaluationRequest(
        type = EvaluationType.PARTIAL,

        sourceCode = draftRevision.sourceCode,
        language = draftRevision.language,

        submissionId = null,
        workspaceId = workspace.id!!,
        assessmentId = workspace.assessmentId,
        questionId = workspace.questionId,
        studentProfileId = workspace.studentProfileId
    )
}
