package io.correcta.workspace.entities

import jakarta.persistence.*
import java.time.Instant
import org.hibernate.annotations.UuidGenerator
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@Entity
@Table(
    name = "assessment_question_workspaces",
    indexes = [
        Index(
            name = "idx_workspace_student_assessment_question",
            columnList = "student_profile_id, assessment_id, question_id",
            unique = true
        ),
        Index(
            name = "idx_workspace_assessment_question",
            columnList = "assessment_id, question_id"
        ),
        Index(
            name = "idx_workspace_student_profile",
            columnList = "student_profile_id"
        )
    ]
)
@EntityListeners(AuditingEntityListener::class)
class AssessmentQuestionWorkspace(
    @Id @UuidGenerator(style = UuidGenerator.Style.TIME)
    var id: String? = null,

    /**
     * assessment-service assessment id
     */
    @Column(name = "assessment_id", nullable = false, updatable = false)
    var assessmentId: String,

    /**
     * assessment-service question id
     */
    @Column(name = "question_id", nullable = false, updatable = false)
    var questionId: String,

    /**
     * identity-service profile id taken from the X-User-Id header
     */
    @Column(name = "student_profile_id", nullable = false, updatable = false)
    var studentProfileId: String,

    @Version
    var entityVersion: Long? = null,

    @Column(name = "current_revision", nullable = false)
    var currentRevision: Int = 0,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: Instant? = null
)
