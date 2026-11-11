package io.correcta.workspace.clients

import io.correcta.workspace.models.response.clients.AssessmentSessionStatusResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

/** HTTP client for the internal API of assessment-service. */
@FeignClient(
  name = "assessment-service",
  url = "\${clients.assessment-service.url}",
)
interface AssessmentClient {

  /** Returns the state of the student's session and whether it still accepts submissions. */
  @GetMapping("/api/v1/internal/assessments/{assessmentId}/sessions/{studentProfileId}")
  fun getSessionSubmissionStatus(
    @PathVariable assessmentId: String,
    @PathVariable studentProfileId: String,
  ): AssessmentSessionStatusResponse
}