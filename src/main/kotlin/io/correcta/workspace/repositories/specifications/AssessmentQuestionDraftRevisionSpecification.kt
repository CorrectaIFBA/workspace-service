package io.correcta.workspace.repositories.specifications

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import org.springframework.data.jpa.domain.Specification

object AssessmentQuestionDraftRevisionSpecification {

  fun hasWorkspaceId(workspaceId: String?): Specification<AssessmentQuestionDraftRevision>? = workspaceId?.let { value ->
    Specification { root, _, cb -> cb.equal(root.get<Any>("workspace").get<String>("id"), value) }
  }

  fun hasRevision(revision: Int?): Specification<AssessmentQuestionDraftRevision>? = revision?.let { value ->
    Specification { root, _, cb -> cb.equal(root.get<Int>("revision"), value) }
  }

  fun hasLanguage(language: String?): Specification<AssessmentQuestionDraftRevision>? = language?.takeIf { it.isNotBlank() }?.let { value ->
    Specification { root, _, cb -> cb.equal(cb.lower(root.get("language")), value.trim().lowercase()) }
  }

  fun filter(
    workspaceId: String? = null,
    revision: Int? = null,
    language: String? = null,
  ): Specification<AssessmentQuestionDraftRevision> = Specification.allOf(
    listOfNotNull(
      hasWorkspaceId(workspaceId),
      hasRevision(revision),
      hasLanguage(language),
    ),
  )
}