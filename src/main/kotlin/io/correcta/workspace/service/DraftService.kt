package io.correcta.workspace.service

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.exceptions.BadRequestException
import io.correcta.workspace.exceptions.PreconditionFailedException
import io.correcta.workspace.models.request.SaveDraftRevisionRequest
import io.correcta.workspace.models.response.SavedDraftRevisionResponse
import io.correcta.workspace.repositories.AssessmentQuestionDraftRevisionRepository
import io.correcta.workspace.repositories.AssessmentQuestionWorkspaceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DraftService(
    val assessmentQuestionDraftRevisionRepository: AssessmentQuestionDraftRevisionRepository,
    val assessmentQuestionWorkspaceRepository: AssessmentQuestionWorkspaceRepository,
    val workspaceReader: WorkspaceReader,
    val submissionWindowGuard: SubmissionWindowGuard,
) {

    @Transactional
    fun createDraftForWorkspace(profileId: String, workspaceId: String, request: SaveDraftRevisionRequest, ifMatch: String): SavedDraftRevisionResponse {
        val workspace = workspaceReader.getOwned(workspaceId, profileId)

        submissionWindowGuard.assertAcceptsSubmissions(workspace.assessmentId, profileId)

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

    companion object {
        private val log = LoggerFactory.getLogger(DraftService::class.java)

        private val REVISION_ETAG = Regex("""^"(0|[1-9][0-9]*)"$""")
    }
}
