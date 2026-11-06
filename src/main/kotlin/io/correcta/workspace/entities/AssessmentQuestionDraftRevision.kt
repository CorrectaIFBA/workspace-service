package io.correcta.workspace.entities

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(
  name = "assessment_question_draft_revisions",
  indexes = [
    Index(
      name = "idx_draft_revision_workspace_revision",
      columnList = "workspace_id, revision",
      unique = true,
    ),
    Index(
      name = "idx_draft_revision_workspace_created_at",
      columnList = "workspace_id, created_at",
    ),
  ],
)
@EntityListeners(AuditingEntityListener::class)
class AssessmentQuestionDraftRevision(
  @Id @UuidGenerator(style = UuidGenerator.Style.TIME)
  var id: String? = null,

  @JoinColumn(name = "workspace_id", nullable = false, updatable = false)
  @ManyToOne(
    optional = false,
  )
  var workspace: AssessmentQuestionWorkspace,

  @Column(nullable = false, updatable = false)
  var language: String,

  @Column(name = "source_code", nullable = false, updatable = false, columnDefinition = "TEXT")
  var sourceCode: String,

  @Column(nullable = false, updatable = false)
  var revision: Int,

  @CreatedDate
  @Column(nullable = false, updatable = false)
  var createdAt: Instant? = null,
)