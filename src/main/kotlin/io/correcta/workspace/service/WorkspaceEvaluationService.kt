package io.correcta.workspace.service

import io.correcta.workspace.messaging.evalution.EvaluationMessaging
import io.correcta.workspace.messaging.outbox.Outbox
import io.correcta.workspace.models.request.EvaluationRequest
import io.correcta.workspace.models.request.PartialEvaluationRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkspaceEvaluationService(
    val workspaceReader: WorkspaceReader,
    val submissionWindowGuard: SubmissionWindowGuard,
    val outbox: Outbox,
) {

    @Transactional
    fun runPartialWorkspaceEvaluation(profileId: String, workspaceId: String) {
        val workspace = workspaceReader.getOwned(workspaceId, profileId)

        submissionWindowGuard.assertAcceptsSubmissions(workspace.assessmentId, profileId)

        val latestDraft = workspaceReader.latestDraft(workspace)

        if (latestDraft == null) {
            log.info("Skipping workspace $workspaceId: no draft to run.")

            return
        }

        triggerEvaluationOrchestrator(PartialEvaluationRequest(draftRevision = latestDraft, workspace = workspace))
    }

    fun triggerEvaluationOrchestrator(request: EvaluationRequest) {
        outbox.enqueue(
            EvaluationMessaging.EXCHANGE,
            EvaluationMessaging.TRIGGER_EVALUATION_KEY,
            request.toPayload()
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(WorkspaceEvaluationService::class.java)
    }
}
