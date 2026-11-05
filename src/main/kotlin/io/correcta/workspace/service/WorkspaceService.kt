package io.correcta.workspace.service

import io.correcta.workspace.clients.AssessmentClient
import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionSubmission
import io.correcta.workspace.entities.AssessmentQuestionSubmissionStatus
import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.exceptions.BadRequestException
import io.correcta.workspace.exceptions.ForbiddenException
import io.correcta.workspace.exceptions.InternalServerErrorException
import io.correcta.workspace.exceptions.NotFoundException
import io.correcta.workspace.exceptions.PreconditionFailedException
import io.correcta.workspace.models.request.CreateWorkspaceRequest
import io.correcta.workspace.models.request.SaveDraftRevisionRequest
import io.correcta.workspace.models.response.SavedDraftRevisionResponse
import io.correcta.workspace.models.response.GetWorkspaceResponse
import io.correcta.workspace.repositories.AssessmentQuestionDraftRevisionRepository
import io.correcta.workspace.repositories.AssessmentQuestionSubmissionRepository
import io.correcta.workspace.repositories.AssessmentQuestionWorkspaceRepository
import io.correcta.workspace.security.components.ScopeEvaluatorComponent
import io.correcta.workspace.security.models.ProfilePrincipal
import io.correcta.workspace.messaging.Outbox
import io.correcta.workspace.messaging.session.SessionDeliveredMessage
import io.correcta.workspace.messaging.session.SessionMessaging
import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class WorkspaceService(
    val assessmentClient: AssessmentClient,
    val assessmentQuestionDraftRevisionRepository: AssessmentQuestionDraftRevisionRepository,
    val assessmentQuestionSubmissionRepository: AssessmentQuestionSubmissionRepository,
    val assessmentQuestionWorkspaceRepository: AssessmentQuestionWorkspaceRepository,
    val scopeEvaluator: ScopeEvaluatorComponent,
    val transactionTemplate: TransactionTemplate,
    val outbox: Outbox,
    val clock: Clock,
) {

    // todo: when a assessment session expires, the assessment-service will send a message to the workspace-service to submit all
    //  the workspaces that are in progress. This will be done by a message queue, and the workspace-service will have a listener
    //  for this message.

    private fun createWorkspace(request: CreateWorkspaceRequest): AssessmentQuestionWorkspace {
        val profile = getAuthenticatedProfile()

        log.info("Creating workspace for student ${profile.profileId}, assessment ${request.assessmentId}, question ${request.questionId}")

        return assessmentQuestionWorkspaceRepository.save(
            AssessmentQuestionWorkspace(
                studentProfileId = profile.profileId,
                assessmentId = request.assessmentId,
                questionId = request.questionId
            )
        )
    }

    fun getOrCreateWorkspaceForAssessmentQuestion(assessmentId: String, questionId: String): GetWorkspaceResponse {
        val profile = getAuthenticatedProfile()

        validateSubmissionStatus(assessmentId, profile.profileId)

        val find = {
            assessmentQuestionWorkspaceRepository.findByStudentProfileIdAndAssessmentIdAndQuestionId(
                studentProfileId = profile.profileId,
                assessmentId = assessmentId,
                questionId = questionId
            ).orElse(null)
        }

        val workspace = find() ?: try {
            createWorkspace(CreateWorkspaceRequest(assessmentId, questionId))
        } catch (e: DataIntegrityViolationException) {
            log.info("Another request/replica created the workspace first: ${e.message}")

            find() ?: throw e
        }

        return buildWorkspaceResponse(workspace)
    }

    @Transactional
    fun createDraftForWorkspace(workspaceId: String, request: SaveDraftRevisionRequest, ifMatch: String): SavedDraftRevisionResponse {
        val profile = getAuthenticatedProfile()

        val workspace = assessmentQuestionWorkspaceRepository.findByIdAndStudentProfileId(
            id = workspaceId,
            studentProfileId = profile.profileId
        ).orElseThrow {
            NotFoundException("Não foi possível encontrar o workspace $workspaceId para o estudante ${profile.profileId}.")
        }

        validateSubmissionStatus(workspace.assessmentId, profile.profileId)

        val currentRevision = workspace.currentRevision
        val expectedRevision = parseRevisionFromEtag(ifMatch)

        if (expectedRevision != currentRevision) {
            throw staleDraft(workspaceId, expectedRevision, currentRevision)
        }

        val nextRevision = currentRevision + 1
        val updatedRows = assessmentQuestionWorkspaceRepository.updateCurrentRevision(
            id = workspaceId,
            currentRevision = nextRevision,
            expectedRevision = currentRevision
        )

        if (updatedRows == 0) {
            throw staleDraft(workspaceId, expectedRevision, currentRevision)
        }

        val savedDraftRevision = assessmentQuestionDraftRevisionRepository.save(
            AssessmentQuestionDraftRevision(
                workspace = workspace,
                revision = nextRevision,
                sourceCode = request.sourceCode,
                language = request.language
            )
        )

        return SavedDraftRevisionResponse.fromDraftRevision(savedDraftRevision)
    }

    /*
     * The student delivers the activity: every workspace is submitted at its latest draft, and only
     * then is assessment-service told to finish the session (session.delivered, via the outbox).
     * Each workspace commits on its own; the message is written after all of them, so the session
     * never ends with an answer left out. A retry skips what is already submitted and writes the
     * message again, which the consumer treats as a no-op.
     */
    fun submitAllWorkspacesForAssessment(assessmentId: String) {
        val profile = getAuthenticatedProfile()

        validateSubmissionStatus(assessmentId, profile.profileId)

        val deliveredAt = clock.instant()

        submitAllWorkspacesForStudent(assessmentId, profile.profileId)

        transactionTemplate.executeWithoutResult {
            outbox.enqueue(
                SessionMessaging.EXCHANGE,
                SessionMessaging.SESSION_DELIVERED_KEY,
                SessionDeliveredMessage(
                    assessmentId = assessmentId,
                    studentProfileId = profile.profileId,
                    deliveredAt = deliveredAt.toString(),
                )
            )
        }
    }

    /*
     * Authentication-free variant for system callers (e.g. the session-expiry queue listener).
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
            // Safe for the client to retry: already submitted workspaces are skipped.
            throw InternalServerErrorException("Falha ao submeter $failures de ${workspaces.size} questões da avaliação $assessmentId.")
        }
    }

    private fun submitLatestDraft(workspaceId: String) {
        val workspace = assessmentQuestionWorkspaceRepository.findById(workspaceId).orElseThrow {
            NotFoundException("Não foi possível encontrar o workspace $workspaceId.")
        }

        val latestDraft = assessmentQuestionDraftRevisionRepository
            .findByWorkspaceIdAndRevision(workspaceId, workspace.currentRevision)
            .orElse(null)

        if (latestDraft == null) {
            log.info("Skipping workspace $workspaceId: no draft to submit.")

            return
        }

        createSubmission(workspace, latestDraft)
    }

    /*
     * Core of every submit. Must run inside a transaction (the past submissions are read with a pessimistic lock).
     * The unique partial index on ACTIVE submissions is the cross-replica safety net.
     */
    private fun createSubmission(workspace: AssessmentQuestionWorkspace, draftRevision: AssessmentQuestionDraftRevision) {
        val workspaceId = workspace.id!!

        // Serialize concurrent submits of this workspace (other replicas included) before reading its submissions.
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

        log.info("Workspace $workspaceId submitted successfully. Submission number: ${submission.submissionNumber}, draft revision: ${draftRevision.revision}")

        // TODO: trigger evaluation-orchestrator for grading the submission. this will be done by rabbitmq, and the evaluation-orchestrator will have a listener for this message.
    }

    private fun staleDraft(workspaceId: String, expected: Int, current: Int): PreconditionFailedException {
        log.warn("Failed to create draft for workspace $workspaceId. Expected: $expected, current: $current. [Optimistic locking failure]")

        return PreconditionFailedException("O rascunho foi atualizado por outra sessão. Expected: $expected, current: $current.")
    }

    /*
     * ETag format: quoted revision number, e.g. "3". No draft yet = "0".
     */
    private fun parseRevisionFromEtag(ifMatch: String): Int {
        val digits = REVISION_ETAG.matchEntire(ifMatch)?.groupValues?.get(1)

        return digits?.toIntOrNull()
            ?: throw BadRequestException("Cabeçalho If-Match inválido: $ifMatch. Formato esperado: \"<revisão>\".")
    }

    private fun validateSubmissionStatus(assessmentId: String, profileId: String) {
        val submissionStatus = assessmentClient.getSessionSubmissionStatus(
            assessmentId = assessmentId,
            studentProfileId = profileId
        )

        if (!submissionStatus.acceptsSubmissions && submissionStatus.late) {
            log.warn("Student $profileId is not allowed to submit answers for assessment $assessmentId because the submission deadline has passed. Submission status: $submissionStatus")

            throw ForbiddenException("O estudante não pode submeter respostas para esta questão, pois o prazo de submissão já expirou.")
        }

        if (!submissionStatus.acceptsSubmissions) {
            log.warn("Student $profileId is not allowed to submit answers for assessment $assessmentId. Submission status: $submissionStatus")

            throw ForbiddenException("O estudante não pode submeter respostas para esta questão.")
        }
    }

    private fun getAuthenticatedProfile(): ProfilePrincipal {
        return scopeEvaluator.currentProfile()
            ?: throw InternalServerErrorException("Falha ao recuperar o principal da autenticação")
    }

    /*
     * Build a GetWorkspaceResponse from a AssessmentQuestionWorkspace entity.
     * Also, it will fetch the most recent draft revision for the workspace and include it in the response.
     * If there is no draft revision, the revision field in the response will be null.
     */
    private fun buildWorkspaceResponse(workspace: AssessmentQuestionWorkspace): GetWorkspaceResponse {
        // currentRevision 0 = no draft yet: skip the query.
        val currentDraft = if (workspace.currentRevision == 0) null else {
            assessmentQuestionDraftRevisionRepository
                .findByWorkspaceIdAndRevision(workspace.id!!, workspace.currentRevision)
                .orElse(null)
        }

        return GetWorkspaceResponse.fromWorkspace(workspace, currentDraft)
    }

    companion object {
        private val log = LoggerFactory.getLogger(WorkspaceService::class.java)

        // Strong ETag only: quoted, non-negative integer without leading zeros. No weak (W/), no "*", no lists.
        private val REVISION_ETAG = Regex("""^"(0|[1-9][0-9]*)"$""")
    }
}
