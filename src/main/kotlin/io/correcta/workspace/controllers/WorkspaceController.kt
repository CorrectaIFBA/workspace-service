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

@RequestMapping("/workspaces")
@Tag(name = "Workspace", description = "Student code workspaces.")
interface WorkspaceController {

  @GetMapping("/assessment/{assessmentId}/questions/{questionId}")
  @IsLoggedUser
  @IsProfileType("STUDENT")
  fun getWorkspaceForAssessmentQuestion(
    @PathVariable assessmentId: String,
    @PathVariable questionId: String,
  ): ResponseEntity<GetWorkspaceResponse>

  @PutMapping("/{workspaceId}/drafts")
  @IsLoggedUser
  @IsProfileType("STUDENT")
  fun createDraftForWorkspace(
    @PathVariable workspaceId: String,
    @Valid @RequestBody body: SaveDraftRevisionRequest,
    @RequestHeader("If-Match") ifMatch: String,
  ): ResponseEntity<SavedDraftRevisionResponse>

  @PostMapping("/{workspaceId}/runs")
  @IsLoggedUser
  @IsProfileType("STUDENT")
  fun runWorkspace(
    @PathVariable workspaceId: String,
  )

  @PostMapping("/assessment/{assessmentId}/submissions")
  @IsLoggedUser
  @IsProfileType("STUDENT")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun submitAllWorkspacesForAssessment(
    @PathVariable assessmentId: String,
  )
}