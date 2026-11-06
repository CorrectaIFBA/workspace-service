package io.correcta.workspace.service

import io.correcta.workspace.entities.AssessmentQuestionDraftRevision
import io.correcta.workspace.entities.AssessmentQuestionSubmission
import io.correcta.workspace.entities.AssessmentQuestionSubmissionStatus
import io.correcta.workspace.entities.AssessmentQuestionWorkspace
import io.correcta.workspace.models.response.clients.AssessmentSessionStatusResponse
import org.mockito.ArgumentMatchers
import java.time.Instant

internal object Fixtures {
  const val PROFILE_ID = "p1"
  const val ASSESSMENT_ID = "a1"
  const val QUESTION_ID = "q1"
  const val WORKSPACE_ID = "w-main"

  fun workspace(id: String = WORKSPACE_ID, revision: Int = 0) = AssessmentQuestionWorkspace(
    id = id,
    assessmentId = ASSESSMENT_ID,
    questionId = QUESTION_ID,
    studentProfileId = PROFILE_ID,
    entityVersion = 0,
    currentRevision = revision,
    createdAt = Instant.now(),
  )

  fun draft(workspace: AssessmentQuestionWorkspace, revision: Int, sourceCode: String = "int main() { return 0; }") = AssessmentQuestionDraftRevision(
    id = "draft-${workspace.id}-$revision",
    workspace = workspace,
    revision = revision,
    sourceCode = sourceCode,
    language = "C",
    createdAt = Instant.now(),
  )

  fun session(acceptsSubmissions: Boolean = true, late: Boolean = false) = AssessmentSessionStatusResponse("s1", "IN_PROGRESS", null, null, acceptsSubmissions, late)

  fun submission(
    workspace: AssessmentQuestionWorkspace,
    draft: AssessmentQuestionDraftRevision,
    number: Int = 1,
    status: AssessmentQuestionSubmissionStatus = AssessmentQuestionSubmissionStatus.ACTIVE,
  ) = AssessmentQuestionSubmission(
    id = "sub-${workspace.id}-$number",
    workspace = workspace,
    submissionNumber = number,
    status = status,
    draftRevision = draft,
  )
}

/** Mockito matcher usable for non-null Kotlin parameters. */
@Suppress("UNCHECKED_CAST")
internal fun <T> anyArg(): T {
  ArgumentMatchers.any<T>()
  return null as T
}

internal fun <T> anyK(): T = anyArg()

internal fun anyStr(): String {
  ArgumentMatchers.anyString()
  return ""
}