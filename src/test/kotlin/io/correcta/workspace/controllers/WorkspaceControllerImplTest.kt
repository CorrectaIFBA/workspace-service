package io.correcta.workspace.controllers

import io.correcta.workspace.controllers.impl.WorkspaceControllerImpl
import io.correcta.workspace.exceptions.InternalServerErrorException
import io.correcta.workspace.security.components.ScopeEvaluatorComponent
import io.correcta.workspace.service.DraftService
import io.correcta.workspace.service.SubmissionService
import io.correcta.workspace.service.WorkspaceEvaluationService
import io.correcta.workspace.service.WorkspaceService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertFailsWith

class WorkspaceControllerImplTest {

  private val scopeEvaluator = mock(ScopeEvaluatorComponent::class.java)
  private val controller = WorkspaceControllerImpl(
    mock(WorkspaceService::class.java),
    mock(DraftService::class.java),
    mock(SubmissionService::class.java),
    mock(WorkspaceEvaluationService::class.java),
    scopeEvaluator,
  )

  @Test
  fun `fails with 500 when the security context has no profile`() {
    `when`(scopeEvaluator.currentProfile()).thenReturn(null)

    assertFailsWith<InternalServerErrorException> { controller.runWorkspace("w1") }
  }
}