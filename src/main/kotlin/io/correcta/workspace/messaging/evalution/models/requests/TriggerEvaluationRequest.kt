package io.correcta.workspace.messaging.evalution.models.requests

import io.correcta.workspace.messaging.evalution.models.enums.EvaluationType

/** Message sent to evaluation-orchestrator. [submissionId] is set only for FINAL evaluations. */
data class TriggerEvaluationRequest(
  val type: EvaluationType,
  val sourceCode: String,
  val language: String,
  val submissionId: String?,
  val workspaceId: String,
  val revision: Int,
  val assessmentId: String,
  val questionId: String,
  val studentProfileId: String,
)