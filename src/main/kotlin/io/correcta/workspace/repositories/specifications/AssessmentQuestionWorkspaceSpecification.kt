package io.correcta.workspace.repositories.specifications

import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import org.springframework.data.jpa.domain.Specification

object AssessmentQuestionWorkspaceSpecification {

    fun hasStudentProfileId(studentProfileId: String?): Specification<AssessmentQuestionWorkspace>? =
        studentProfileId?.let { value ->
            Specification { root, _, cb -> cb.equal(root.get<String>("studentProfileId"), value) }
        }

    fun hasAssessmentId(assessmentId: String?): Specification<AssessmentQuestionWorkspace>? =
        assessmentId?.let { value ->
            Specification { root, _, cb -> cb.equal(root.get<String>("assessmentId"), value) }
        }

    fun hasQuestionId(questionId: String?): Specification<AssessmentQuestionWorkspace>? =
        questionId?.let { value ->
            Specification { root, _, cb -> cb.equal(root.get<String>("questionId"), value) }
        }

    fun filter(
        studentProfileId: String? = null,
        assessmentId: String? = null,
        questionId: String? = null,
    ): Specification<AssessmentQuestionWorkspace> =
        Specification.allOf(
            listOfNotNull(
                hasStudentProfileId(studentProfileId),
                hasAssessmentId(assessmentId),
                hasQuestionId(questionId),
            )
        )
}
