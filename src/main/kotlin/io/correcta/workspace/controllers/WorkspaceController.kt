package io.correcta.workspace.controllers

import io.correcta.workspace.models.request.SaveDraftRevisionRequest
import io.correcta.workspace.models.response.GetWorkspaceResponse
import io.correcta.workspace.models.response.SavedDraftRevisionResponse
import io.correcta.workspace.security.annotations.IsLoggedUser
import io.correcta.workspace.security.annotations.IsProfileType
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Student-facing API. Every endpoint requires a logged-in STUDENT; the profile comes from the gateway's identity headers.
 */
@RequestMapping("/workspaces")
@Tag(name = "Workspace", description = "Student code workspaces.")
interface WorkspaceController {

  /** Returns the student's workspace for a question, creating it on first access. The ETag is the current revision. */
  @GetMapping("/assessment/{assessmentId}/questions/{questionId}")
  @IsLoggedUser
  @IsProfileType("STUDENT")
  fun getWorkspaceForAssessmentQuestion(
    @PathVariable assessmentId: String,
    @PathVariable questionId: String,
  ): ResponseEntity<GetWorkspaceResponse>

  /** Saves a new draft revision. `If-Match` must hold the current ETag; the response carries the new one. */
  @PutMapping("/{workspaceId}/drafts")
  @IsLoggedUser
  @IsProfileType("STUDENT")
  fun createDraftForWorkspace(
    @PathVariable workspaceId: String,
    @Valid @RequestBody body: SaveDraftRevisionRequest,
    @RequestHeader("If-Match") ifMatch: String,
  ): ResponseEntity<SavedDraftRevisionResponse>

  /** Requests a partial evaluation of the latest draft. No submission is created. */
  @PostMapping("/{workspaceId}/runs")
  @IsLoggedUser
  @IsProfileType("STUDENT")
  fun runWorkspace(
    @PathVariable workspaceId: String,
  )

  /** Delivers the assessment: submits the latest draft of every workspace and closes the session. */
  @PostMapping("/assessment/{assessmentId}/submissions")
  @IsLoggedUser
  @IsProfileType("STUDENT")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun submitAllWorkspacesForAssessment(
    @PathVariable assessmentId: String,
  )
}