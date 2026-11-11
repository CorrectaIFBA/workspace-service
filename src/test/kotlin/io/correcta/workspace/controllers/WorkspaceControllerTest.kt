package io.correcta.workspace.controllers

import com.jayway.jsonpath.JsonPath
import io.correcta.workspace.clients.AssessmentClient
import io.correcta.workspace.models.response.clients.AssessmentSessionStatusResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

/**
 * HTTP-level contract: status codes, ETag / If-Match, validation. Runs on H2 (schema from the entities).
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceControllerTest(
  @Autowired val mockMvc: MockMvc,
) {

  @MockitoBean
  private lateinit var assessmentClient: AssessmentClient

  private val assessmentId = "a-${UUID.randomUUID()}"

  @BeforeEach
  fun openSession() {
    `when`(assessmentClient.getSessionSubmissionStatus(anyString(), anyString()))
      .thenReturn(AssessmentSessionStatusResponse("s1", "IN_PROGRESS", null, null, acceptsSubmissions = true, late = false))
  }

  @Test
  fun `GET workspace returns 200 with ETag of revision 0 for a new workspace`() {
    getWorkspace("q1").andExpect {
      status { isOk() }
      header { string("ETag", "\"0\"") }
      jsonPath("$.currentRevision") { value(0) }
    }
  }

  @Test
  fun `GET workspace is unauthorized without identity headers`() {
    mockMvc.get("/workspaces/assessment/$assessmentId/questions/q1").andExpect { status { isUnauthorized() } }
  }

  @ParameterizedTest
  @ValueSource(strings = ["FACULTY", "STAFF"])
  fun `GET workspace is forbidden for non-student profile types`(profileType: String) {
    mockMvc.get("/workspaces/assessment/$assessmentId/questions/q1") {
      header("X-User-Id", PROFILE_ID)
      header("X-User-Profile-Type", profileType)
    }.andExpect { status { isForbidden() } }
  }

  @Test
  fun `GET workspace is forbidden without a profile type`() {
    mockMvc.get("/workspaces/assessment/$assessmentId/questions/q1") { header("X-User-Id", PROFILE_ID) }
      .andExpect { status { isForbidden() } }
  }

  @Test
  fun `GET workspace ETag follows the latest saved draft`() {
    val id = workspaceId("q1")
    saveDraft(id, "\"0\"").andExpect { status { isOk() } }

    getWorkspace("q1").andExpect {
      header { string("ETag", "\"1\"") }
      jsonPath("$.currentDraft.revision") { value(1) }
    }
  }

  @Test
  fun `PUT draft with matching If-Match returns the new ETag`() {
    val id = workspaceId("q1")

    saveDraft(id, "\"0\"").andExpect {
      status { isOk() }
      header { string("ETag", "\"1\"") }
      jsonPath("$.revision") { value(1) }
    }
    saveDraft(id, "\"1\"").andExpect {
      status { isOk() }
      header { string("ETag", "\"2\"") }
    }
  }

  @Test
  fun `PUT draft without If-Match returns 428`() {
    val id = workspaceId("q1")

    mockMvc.put("/workspaces/$id/drafts") {
      header("X-User-Id", PROFILE_ID)
      header("X-User-Profile-Type", "STUDENT")
      contentType = MediaType.APPLICATION_JSON
      content = draftBody()
    }.andExpect { status { isPreconditionRequired() } }
  }

  @Test
  fun `PUT draft with stale If-Match returns 412`() {
    val id = workspaceId("q1")
    saveDraft(id, "\"0\"").andExpect { status { isOk() } }

    saveDraft(id, "\"0\"").andExpect { status { isPreconditionFailed() } }
  }

  @ParameterizedTest
  @ValueSource(strings = ["0", "W/\"0\"", "*", "\"abc\"", "\"00\""])
  fun `PUT draft with malformed If-Match returns 400`(ifMatch: String) {
    val id = workspaceId("q1")

    saveDraft(id, ifMatch).andExpect { status { isBadRequest() } }
  }

  @Test
  fun `PUT draft with blank code returns 400`() {
    val id = workspaceId("q1")

    saveDraft(id, "\"0\"", body = """{"sourceCode":"  ","language":"C"}""").andExpect { status { isBadRequest() } }
  }

  @Test
  fun `PUT draft above the size limit returns 400`() {
    val id = workspaceId("q1")
    val huge = "a".repeat(65_537)

    saveDraft(id, "\"0\"", body = """{"sourceCode":"$huge","language":"C"}""").andExpect { status { isBadRequest() } }
  }

  @Test
  fun `PUT draft on another student's workspace returns 404`() {
    val id = workspaceId("q1")

    saveDraft(id, "\"0\"", profileId = "someone-else").andExpect { status { isNotFound() } }
  }

  @Test
  fun `PUT draft after the deadline returns 403`() {
    val id = workspaceId("q1")
    `when`(assessmentClient.getSessionSubmissionStatus(anyString(), anyString()))
      .thenReturn(AssessmentSessionStatusResponse("s1", "EXPIRED", null, null, acceptsSubmissions = false, late = true))

    saveDraft(id, "\"0\"").andExpect { status { isForbidden() } }
  }

  @Test
  fun `POST run returns 200 for the latest draft`() {
    val id = workspaceId("q1")
    saveDraft(id, "\"0\"").andExpect { status { isOk() } }

    mockMvc.post("/workspaces/$id/runs") {
      header("X-User-Id", PROFILE_ID)
      header("X-User-Profile-Type", "STUDENT")
    }.andExpect { status { isOk() } }
  }

  @Test
  fun `a single question cannot be submitted on its own`() {
    val id = workspaceId("q1")
    saveDraft(id, "\"0\"").andExpect { status { isOk() } }

    mockMvc.post("/workspaces/$id/submissions") {
      header("X-User-Id", PROFILE_ID)
      header("X-User-Profile-Type", "STUDENT")
      header("If-Match", "\"1\"")
    }.andExpect { status { is4xxClientError() } }
  }

  @Test
  fun `POST submit-all returns 202 and needs no If-Match`() {
    val q1 = workspaceId("q1")
    workspaceId("q2")
    saveDraft(q1, "\"0\"").andExpect { status { isOk() } }

    mockMvc.post("/workspaces/assessment/$assessmentId/submissions") {
      header("X-User-Id", PROFILE_ID)
      header("X-User-Profile-Type", "STUDENT")
    }
      .andExpect {
        status { isAccepted() }
        content { string("") }
      }
  }

  @Test
  fun `POST submit-all after the deadline returns 403`() {
    workspaceId("q1")
    `when`(assessmentClient.getSessionSubmissionStatus(anyString(), anyString()))
      .thenReturn(AssessmentSessionStatusResponse("s1", "EXPIRED", null, null, acceptsSubmissions = false, late = true))

    mockMvc.post("/workspaces/assessment/$assessmentId/submissions") {
      header("X-User-Id", PROFILE_ID)
      header("X-User-Profile-Type", "STUDENT")
    }
      .andExpect { status { isForbidden() } }
  }

  private fun getWorkspace(questionId: String, profileId: String = PROFILE_ID): ResultActionsDsl = mockMvc.get("/workspaces/assessment/$assessmentId/questions/$questionId") {
    header("X-User-Id", profileId)
    header("X-User-Profile-Type", "STUDENT")
  }

  private fun workspaceId(questionId: String): String {
    val body = getWorkspace(questionId).andReturn().response.contentAsString

    return JsonPath.read(body, "$.id")
  }

  private fun draftBody(code: String = "int main() { return 0; }") = """{"sourceCode":"$code","language":"C"}"""

  private fun saveDraft(
    workspaceId: String,
    ifMatch: String,
    body: String = draftBody(),
    profileId: String = PROFILE_ID,
  ): ResultActionsDsl = mockMvc.put("/workspaces/$workspaceId/drafts") {
    header("X-User-Id", profileId)
    header("X-User-Profile-Type", "STUDENT")
    header("If-Match", ifMatch)
    contentType = MediaType.APPLICATION_JSON
    content = body
  }

  companion object {
    private const val PROFILE_ID = "student-1"
  }
}