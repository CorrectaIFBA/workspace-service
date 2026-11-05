package io.correcta.workspace.repositories

import io.correcta.workspace.entities.AssessmentQuestionSubmission
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.Optional
import org.springframework.data.jpa.repository.Lock

@Repository
interface AssessmentQuestionSubmissionRepository :
    JpaRepository<AssessmentQuestionSubmission, String>,
    JpaSpecificationExecutor<AssessmentQuestionSubmission> {

    fun findByIdAndWorkspaceStudentProfileId(id: String, studentProfileId: String): Optional<AssessmentQuestionSubmission>

    fun findFirstByWorkspaceIdOrderBySubmissionNumberDesc(workspaceId: String): Optional<AssessmentQuestionSubmission>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findTopByWorkspaceIdOrderBySubmissionNumberDesc(workspaceId: String): Optional<AssessmentQuestionSubmission>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByWorkspaceIdOrderBySubmissionNumberDesc(workspaceId: String): List<AssessmentQuestionSubmission>
}
