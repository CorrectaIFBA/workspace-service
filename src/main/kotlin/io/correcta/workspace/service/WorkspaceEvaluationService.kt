package io.correcta.workspace.service

import io.correcta.workspace.messaging.evalution.EvaluationMessaging
import io.correcta.workspace.messaging.outbox.Outbox
import io.correcta.workspace.models.request.EvaluationRequest
import io.correcta.workspace.models.request.PartialEvaluationRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Requests evaluations from evaluation-orchestrator by writing them to the outbox
 * (delivered to RabbitMQ afterwards, at least once).
 */
@Service
class WorkspaceEvaluationService(
  val workspaceReader: WorkspaceReader,
  val submissionWindowGuard: SubmissionWindowGuard,
  val outbox: Outbox,
) {

  /**
   * "Run" button: evaluates the student's latest draft without creating a submission.
   * Needs a transaction because [io.correcta.workspace.messaging.outbox.Outbox.enqueue] refuses to run outside one.
   */
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

  /** Queues the evaluation described by [request]. Must be called inside a transaction. */
  fun triggerEvaluationOrchestrator(request: EvaluationRequest) {
    outbox.enqueue(
      EvaluationMessaging.EXCHANGE,
      EvaluationMessaging.TRIGGER_EVALUATION_KEY,
      request.toPayload(),
    )
  }

  companion object {
    private val log = LoggerFactory.getLogger(WorkspaceEvaluationService::class.java)
  }
}