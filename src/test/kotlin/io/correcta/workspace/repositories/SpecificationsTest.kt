package io.correcta.workspace.repositories

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionSubmission
import io.correcta.workspace.entities.AssessmentQuestionSubmissionStatus
import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.repositories.specifications.AssessmentQuestionDraftRevisionSpecification
import io.correcta.workspace.repositories.specifications.AssessmentQuestionSubmissionSpecification
import io.correcta.workspace.repositories.specifications.AssessmentQuestionWorkspaceSpecification
import io.correcta.workspace.repositories.specifications.SpecificationExt
import io.correcta.workspace.repositories.specifications.SpecificationExt.toContainsPattern
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the specifications against H2 so wrong attribute paths fail here, not in production. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class SpecificationsTest(
  @Autowired val workspaces: AssessmentQuestionWorkspaceRepository,
  @Autowired val drafts: AssessmentQuestionDraftRevisionRepository,
  @Autowired val submissions: AssessmentQuestionSubmissionRepository,
) {

  private val assessmentId = "a-${UUID.randomUUID()}"

  private lateinit var mine: AssessmentQuestionWorkspace
  private lateinit var cDraft: AssessmentQuestionDraftRevision
  private lateinit var javaDraft: AssessmentQuestionDraftRevision

  @BeforeEach
  fun seed() {
    mine = workspaces.save(AssessmentQuestionWorkspace(assessmentId = assessmentId, questionId = "q1", studentProfileId = "p1"))
    workspaces.save(AssessmentQuestionWorkspace(assessmentId = assessmentId, questionId = "q2", studentProfileId = "p2"))
    cDraft = drafts.save(AssessmentQuestionDraftRevision(workspace = mine, language = "C", sourceCode = "c", revision = 1))
    javaDraft = drafts.save(AssessmentQuestionDraftRevision(workspace = mine, language = "Java", sourceCode = "j", revision = 2))
    submissions.save(
      AssessmentQuestionSubmission(
        workspace = mine,
        submissionNumber = 1,
        status = AssessmentQuestionSubmissionStatus.SUPERSEDED,
        draftRevision = cDraft,
      ),
    )
    submissions.save(AssessmentQuestionSubmission(workspace = mine, submissionNumber = 2, draftRevision = javaDraft))
  }

  @Test
  fun `workspace filters combine with AND and ignore null arguments`() {
    val all = workspaces.findAll(AssessmentQuestionWorkspaceSpecification.filter(assessmentId = assessmentId))
    val one = workspaces.findAll(AssessmentQuestionWorkspaceSpecification.filter("p1", assessmentId, "q1"))
    val none = workspaces.findAll(AssessmentQuestionWorkspaceSpecification.filter("p1", assessmentId, "q2"))

    assertEquals(2, all.size)
    assertEquals(listOf(mine.id), one.map { it.id })
    assertEquals(0, none.size)
  }

  @Test
  fun `draft filters match workspace, revision and language ignoring case and blanks`() {
    val byLanguage = drafts.findAll(AssessmentQuestionDraftRevisionSpecification.filter(mine.id, language = " java "))
    val byRevision = drafts.findAll(AssessmentQuestionDraftRevisionSpecification.filter(mine.id, revision = 1))
    val blankLanguage = drafts.findAll(AssessmentQuestionDraftRevisionSpecification.filter(mine.id, language = " "))

    assertEquals(listOf(javaDraft.id), byLanguage.map { it.id })
    assertEquals(listOf(cDraft.id), byRevision.map { it.id })
    assertEquals(2, blankLanguage.size)
  }

  @Test
  fun `submission filters match workspace, student, status and draft language`() {
    val spec = AssessmentQuestionSubmissionSpecification

    assertEquals(2, submissions.findAll(spec.filter(workspaceId = mine.id, language = "")).size)
    assertEquals(listOf(2), submissions.findAll(spec.filter(mine.id, "p1", AssessmentQuestionSubmissionStatus.ACTIVE)).map { it.submissionNumber })
    assertEquals(listOf(1), submissions.findAll(spec.filter(mine.id, language = "c")).map { it.submissionNumber })
    assertEquals(0, submissions.findAll(spec.filter(mine.id, studentProfileId = "p2")).size)
  }

  @Test
  fun `filters without arguments match every row`() {
    assertTrue(workspaces.findAll(AssessmentQuestionWorkspaceSpecification.filter()).size >= 2)
    assertTrue(drafts.findAll(AssessmentQuestionDraftRevisionSpecification.filter()).size >= 2)
    assertTrue(submissions.findAll(AssessmentQuestionSubmissionSpecification.filter()).size >= 2)
  }

  @Test
  fun `contains pattern lowercases, trims and escapes LIKE wildcards`() {
    assertEquals("%a\\%b\\_c\\\\d%", "  A%b_C\\d ".toContainsPattern())
    assertEquals('\\', SpecificationExt.LIKE_ESCAPE_CHAR)
  }
}