package io.correcta.workspace.repositories.specifications

import io.correcta.workspace.entities.AssessmentQuestionSubmission
import io.correcta.workspace.entities.AssessmentQuestionSubmissionStatus
import org.springframework.data.jpa.domain.Specification

object AssessmentQuestionSubmissionSpecification {

  fun hasWorkspaceId(workspaceId: String?): Specification<AssessmentQuestionSubmission>? = workspaceId?.let { value ->
    Specification { root, _, cb -> cb.equal(root.get<Any>("workspace").get<String>("id"), value) }
  }

  fun hasStudentProfileId(studentProfileId: String?): Specification<AssessmentQuestionSubmission>? = studentProfileId?.let { value ->
    Specification { root, _, cb ->
      cb.equal(root.get<Any>("workspace").get<String>("studentProfileId"), value)
    }
  }

  fun hasStatus(status: AssessmentQuestionSubmissionStatus?): Specification<AssessmentQuestionSubmission>? = status?.let { value ->
    Specification { root, _, cb -> cb.equal(root.get<AssessmentQuestionSubmissionStatus>("status"), value) }
  }

  fun hasLanguage(language: String?): Specification<AssessmentQuestionSubmission>? = language?.takeIf { it.isNotBlank() }?.let { value ->
    Specification { root, _, cb -> cb.equal(cb.lower(root.get("language")), value.trim().lowercase()) }
  }

  fun filter(
    workspaceId: String? = null,
    studentProfileId: String? = null,
    status: AssessmentQuestionSubmissionStatus? = null,
    language: String? = null,
  ): Specification<AssessmentQuestionSubmission> = Specification.allOf(
    listOfNotNull(
      hasWorkspaceId(workspaceId),
      hasStudentProfileId(studentProfileId),
      hasStatus(status),
      hasLanguage(language),
    ),
  )
}