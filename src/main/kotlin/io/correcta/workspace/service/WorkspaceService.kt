package io.correcta.workspace.service

import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.models.request.CreateWorkspaceRequest
import io.correcta.workspace.models.response.GetWorkspaceResponse
import io.correcta.workspace.repositories.AssessmentQuestionWorkspaceRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class WorkspaceService(
  val assessmentQuestionWorkspaceRepository: AssessmentQuestionWorkspaceRepository,
  val workspaceReader: WorkspaceReader,
  val submissionWindowGuard: SubmissionWindowGuard,
) {

  private fun createWorkspace(profileId: String, request: CreateWorkspaceRequest): AssessmentQuestionWorkspace {
    log.info("Creating workspace for student $profileId, assessment ${request.assessmentId}, question ${request.questionId}")

    return assessmentQuestionWorkspaceRepository.save(
      AssessmentQuestionWorkspace(
        studentProfileId = profileId,
        assessmentId = request.assessmentId,
        questionId = request.questionId,
      ),
    )
  }

  fun getOrCreateWorkspaceForAssessmentQuestion(profileId: String, assessmentId: String, questionId: String): GetWorkspaceResponse {
    submissionWindowGuard.assertAcceptsSubmissions(assessmentId, profileId)

    val find = {
      assessmentQuestionWorkspaceRepository.findByStudentProfileIdAndAssessmentIdAndQuestionId(
        studentProfileId = profileId,
        assessmentId = assessmentId,
        questionId = questionId,
      ).orElse(null)
    }

    val workspace = find() ?: try {
      createWorkspace(profileId, CreateWorkspaceRequest(assessmentId, questionId))
    } catch (e: DataIntegrityViolationException) {
      log.info("Another request/replica created the workspace first: ${e.message}")

      find() ?: throw e
    }

    return buildWorkspaceResponse(workspace)
  }

  private fun buildWorkspaceResponse(workspace: AssessmentQuestionWorkspace): GetWorkspaceResponse {
    val currentDraft = workspaceReader.latestDraft(workspace)

    return GetWorkspaceResponse.fromWorkspace(workspace, currentDraft)
  }

  companion object {
    private val log = LoggerFactory.getLogger(WorkspaceService::class.java)
  }
}