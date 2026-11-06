package io.correcta.workspace.models.request

import io.correcta.workspace.messaging.evalution.models.requests.TriggerEvaluationRequest

sealed interface EvaluationRequest {
    fun toPayload(): TriggerEvaluationRequest
}
