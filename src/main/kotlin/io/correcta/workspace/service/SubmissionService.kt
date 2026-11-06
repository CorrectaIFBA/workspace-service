package io.correcta.workspace.service

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionSubmission
import io.correcta.workspace.entities.AssessmentQuestionSubmissionStatus
import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.exceptions.InternalServerErrorException
import io.correcta.workspace.exceptions.NotFoundException
import io.correcta.workspace.messaging.outbox.Outbox
import io.correcta.workspace.messaging.session.SessionDeliveredMessage
import io.correcta.workspace.messaging.session.SessionMessaging
import io.correcta.workspace.models.request.FinalEvaluationRequest
import io.correcta.workspace.repositories.AssessmentQuestionSubmissionRepository
import io.correcta.workspace.repositories.AssessmentQuestionWorkspaceRepository
import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class SubmissionService(
    val assessmentQuestionSubmissionRepository: AssessmentQuestionSubmissionRepository,
    val assessmentQuestionWorkspaceRepository: AssessmentQuestionWorkspaceRepository,
    val workspaceEvaluationService: WorkspaceEvaluationService,
    val workspaceReader: WorkspaceReader,
    val submissionWindowGuard: SubmissionWindowGuard,
    val transactionTemplate: TransactionTemplate,
    val outbox: Outbox,
    val clock: Clock,
) {

    fun submitAllWorkspacesForAssessment(profileId: String, assessmentId: String) {
        submissionWindowGuard.assertAcceptsSubmissions(assessmentId, profileId)

        val deliveredAt = clock.instant()

        submitAllWorkspacesForStudent(assessmentId, profileId)

        transactionTemplate.executeWithoutResult {
            outbox.enqueue(
                SessionMessaging.EXCHANGE,
                SessionMessaging.SESSION_DELIVERED_KEY,
                SessionDeliveredMessage(
                    assessmentId = assessmentId,
                    studentProfileId = profileId,
                    deliveredAt = deliveredAt.toString(),
                )
            )
        }
    }

    /*
     * Also used by system callers (e.g. the session-expiry queue listener).
     * It does NOT check the deadline: the caller is acting precisely because the session ended.
     */
    fun submitAllWorkspacesForStudent(assessmentId: String, studentProfileId: String) {
        val workspaces = assessmentQuestionWorkspaceRepository
            .findByStudentProfileIdAndAssessmentId(studentProfileId, assessmentId)

        var failures = 0

        for (workspace in workspaces) {
            try {
                transactionTemplate.executeWithoutResult { submitLatestDraft(workspace.id!!) }
            } catch (e: Exception) {
                failures++

                log.error("Failed to submit workspace ${workspace.id} of assessment $assessmentId for student $studentProfileId.", e)
            }
        }

        if (failures > 0) {
            throw InternalServerErrorException("Falha ao submeter $failures de ${workspaces.size} questões da avaliação $assessmentId.")
        }
    }

    private fun submitLatestDraft(workspaceId: String) {
        val workspace = assessmentQuestionWorkspaceRepository.findById(workspaceId).orElseThrow {
            NotFoundException("Não foi possível encontrar o workspace $workspaceId.")
        }

        val latestDraft = workspaceReader.latestDraft(workspace)

        if (latestDraft == null) {
            log.info("Skipping workspace $workspaceId: no draft to submit.")

            return
        }

        createSubmission(workspace, latestDraft)
    }

    private fun createSubmission(workspace: AssessmentQuestionWorkspace, draftRevision: AssessmentQuestionDraftRevision) {
        val workspaceId = workspace.id!!

        assessmentQuestionWorkspaceRepository.findByIdForUpdate(workspaceId)

        val pastSubmissions = assessmentQuestionSubmissionRepository.findByWorkspaceIdOrderBySubmissionNumberDesc(workspaceId)
        val latestSubmission = pastSubmissions.firstOrNull()

        if (latestSubmission != null &&
            latestSubmission.status == AssessmentQuestionSubmissionStatus.ACTIVE &&
            latestSubmission.draftRevision.revision == draftRevision.revision
        ) {
            log.info("Workspace $workspaceId already has an ACTIVE submission for draft revision ${draftRevision.revision}. Nothing to do.")

            return
        }

        for (submission in pastSubmissions.filter { it.status == AssessmentQuestionSubmissionStatus.ACTIVE }) {
            submission.status = AssessmentQuestionSubmissionStatus.SUPERSEDED
        }

        assessmentQuestionSubmissionRepository.saveAllAndFlush(pastSubmissions)

        val submission = assessmentQuestionSubmissionRepository.save(
            AssessmentQuestionSubmission(
                workspace = workspace,
                draftRevision = draftRevision,
                submissionNumber = (latestSubmission?.submissionNumber ?: 0) + 1,
            )
        )

        workspaceEvaluationService.triggerEvaluationOrchestrator(
            request = FinalEvaluationRequest(
                submission = submission,
                workspace = workspace
            )
        )

        log.info("Workspace $workspaceId submitted successfully. Submission number: ${submission.submissionNumber}, draft revision: ${draftRevision.revision}")
    }

    companion object {
        private val log = LoggerFactory.getLogger(SubmissionService::class.java)
    }
}
