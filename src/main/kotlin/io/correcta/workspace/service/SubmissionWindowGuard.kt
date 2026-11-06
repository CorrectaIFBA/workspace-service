package io.correcta.workspace.service

import io.correcta.workspace.clients.AssessmentClient
import io.correcta.workspace.exceptions.ForbiddenException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SubmissionWindowGuard(
  val assessmentClient: AssessmentClient,
) {

  fun assertAcceptsSubmissions(assessmentId: String, profileId: String) {
    val submissionStatus = assessmentClient.getSessionSubmissionStatus(
      assessmentId = assessmentId,
      studentProfileId = profileId,
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

  companion object {
    private val log = LoggerFactory.getLogger(SubmissionWindowGuard::class.java)
  }
}