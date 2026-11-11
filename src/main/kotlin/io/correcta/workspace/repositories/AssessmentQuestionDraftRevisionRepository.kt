package io.correcta.workspace.repositories

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.Optional

/** Persistence of [AssessmentQuestionDraftRevision]. */
@Repository
interface AssessmentQuestionDraftRevisionRepository :
  JpaRepository<AssessmentQuestionDraftRevision, String>,
  JpaSpecificationExecutor<AssessmentQuestionDraftRevision> {

  fun findByWorkspaceIdAndRevision(workspaceId: String, revision: Int): Optional<AssessmentQuestionDraftRevision>

  fun findFirstByWorkspaceIdOrderByRevisionDesc(workspaceId: String): Optional<AssessmentQuestionDraftRevision>

  fun findTopByWorkspaceIdOrderByRevisionDesc(workspaceId: String): Optional<AssessmentQuestionDraftRevision>
}