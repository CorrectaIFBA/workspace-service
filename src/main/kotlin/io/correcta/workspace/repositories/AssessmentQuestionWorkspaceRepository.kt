package io.correcta.workspace.repositories

import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface AssessmentQuestionWorkspaceRepository :
  JpaRepository<AssessmentQuestionWorkspace, String>,
  JpaSpecificationExecutor<AssessmentQuestionWorkspace> {

  fun findByStudentProfileIdAndAssessmentIdAndQuestionId(
    studentProfileId: String,
    assessmentId: String,
    questionId: String,
  ): Optional<AssessmentQuestionWorkspace>

  fun findByStudentProfileIdAndAssessmentId(studentProfileId: String, assessmentId: String): List<AssessmentQuestionWorkspace>

  fun findByIdAndStudentProfileId(id: String, studentProfileId: String): Optional<AssessmentQuestionWorkspace>

  /**
   * Row lock used to serialize submissions per workspace. Locking the submission rows is not enough:
   * when none exist yet there is nothing to lock and concurrent first submits would race.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from AssessmentQuestionWorkspace w where w.id = :id")
  fun findByIdForUpdate(id: String): Optional<AssessmentQuestionWorkspace>

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    """
        update AssessmentQuestionWorkspace
        set currentRevision = :currentRevision,
            entityVersion = entityVersion + 1,
            updatedAt = instant
        where id = :id
          and currentRevision = :expectedRevision
    """,
  )
  fun updateCurrentRevision(
    id: String,
    currentRevision: Int,
    expectedRevision: Int,
  ): Int
}