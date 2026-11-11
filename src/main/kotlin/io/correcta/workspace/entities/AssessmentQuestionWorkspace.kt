package io.correcta.workspace.entities

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * A student's working area for one question of one assessment. Unique per (student, assessment, question).
 * [currentRevision] points at the latest saved draft (0 = none) and doubles as the optimistic-lock token behind `If-Match`.
 */
@Entity
@Table(
  name = "assessment_question_workspaces",
  indexes = [
    Index(
      name = "idx_workspace_student_assessment_question",
      columnList = "student_profile_id, assessment_id, question_id",
      unique = true,
    ),
    Index(
      name = "idx_workspace_assessment_question",
      columnList = "assessment_id, question_id",
    ),
    Index(
      name = "idx_workspace_student_profile",
      columnList = "student_profile_id",
    ),
  ],
)
@EntityListeners(AuditingEntityListener::class)
class AssessmentQuestionWorkspace(
  @Id @UuidGenerator(style = UuidGenerator.Style.TIME)
  var id: String? = null,

  /** Assessment id in assessment-service. */
  @Column(name = "assessment_id", nullable = false, updatable = false)
  var assessmentId: String,

  /** Question id in assessment-service. */
  @Column(name = "question_id", nullable = false, updatable = false)
  var questionId: String,

  /** Student profile id in identity-service, taken from the `X-User-Id` header. */
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
  var updatedAt: Instant? = null,
)