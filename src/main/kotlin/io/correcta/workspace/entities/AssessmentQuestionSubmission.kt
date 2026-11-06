package io.correcta.workspace.entities

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(
  name = "assessment_question_submissions",
  indexes = [
    Index(
      name = "idx_submission_workspace_created_at",
      columnList = "workspace_id, created_at",
    ),
    Index(
      name = "idx_submission_workspace_submission_number",
      columnList = "workspace_id, submission_number",
      unique = true,
    ),
  ],
)
@EntityListeners(AuditingEntityListener::class)
class AssessmentQuestionSubmission(
  @Id
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  var id: String? = null,

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(
    name = "workspace_id",
    nullable = false,
    updatable = false,
  )
  var workspace: AssessmentQuestionWorkspace,

  @Column(name = "submission_number", nullable = false, updatable = false)
  var submissionNumber: Int,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: AssessmentQuestionSubmissionStatus = AssessmentQuestionSubmissionStatus.ACTIVE,

  @JoinColumn(name = "draft_revision_id", nullable = false, updatable = false)
  @OneToOne(
    optional = false,
    fetch = FetchType.LAZY,
  )
  var draftRevision: AssessmentQuestionDraftRevision,

  @CreatedDate
  @Column(nullable = false, updatable = false)
  var createdAt: Instant? = null,
)