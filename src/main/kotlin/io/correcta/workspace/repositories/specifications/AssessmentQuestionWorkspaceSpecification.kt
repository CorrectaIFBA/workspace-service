package io.correcta.workspace.repositories.specifications

import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import org.springframework.data.jpa.domain.Specification

/** Optional filters for workspaces. A null argument adds no condition. */
object AssessmentQuestionWorkspaceSpecification {

  fun hasStudentProfileId(studentProfileId: String?): Specification<AssessmentQuestionWorkspace>? = studentProfileId?.let { value ->
    Specification { root, _, cb -> cb.equal(root.get<String>("studentProfileId"), value) }
  }

  fun hasAssessmentId(assessmentId: String?): Specification<AssessmentQuestionWorkspace>? = assessmentId?.let { value ->
    Specification { root, _, cb -> cb.equal(root.get<String>("assessmentId"), value) }
  }

  fun hasQuestionId(questionId: String?): Specification<AssessmentQuestionWorkspace>? = questionId?.let { value ->
    Specification { root, _, cb -> cb.equal(root.get<String>("questionId"), value) }
  }

  /** Combines every non-null filter with AND. */
  fun filter(
    studentProfileId: String? = null,
    assessmentId: String? = null,
    questionId: String? = null,
  ): Specification<AssessmentQuestionWorkspace> = Specification.allOf(
    listOfNotNull(
      hasStudentProfileId(studentProfileId),
      hasAssessmentId(assessmentId),
      hasQuestionId(questionId),
    ),
  )
}