package io.correcta.workspace.models.request

import io.correcta.workspace.messaging.evalution.models.requests.TriggerEvaluationRequest

/** An evaluation to request from evaluation-orchestrator: either a partial run or a final one. */
sealed interface EvaluationRequest {
  /** Builds the message sent to evaluation-orchestrator. */
  fun toPayload(): TriggerEvaluationRequest
}