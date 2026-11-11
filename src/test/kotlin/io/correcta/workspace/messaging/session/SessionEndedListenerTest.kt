package io.correcta.workspace.messaging.session

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.correcta.workspace.service.SubmissionService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import kotlin.test.assertEquals

class SessionEndedListenerTest {

  private val objectMapper = jacksonObjectMapper()
  private val submissionService = mock(SubmissionService::class.java)
  private val listener = SessionEndedListener(submissionService, objectMapper)

  private val json =
    """{"sessionId":"s1","assessmentId":"a1","studentProfileId":"p1","state":"ENDED","reason":"EXPIRED","endedAt":"2030-03-10T13:00:00Z"}"""

  @Test
  fun `submits the drafts of the student whose session ended`() {
    listener.onSessionEnded(Message(json.toByteArray(), MessageProperties()))

    verify(submissionService).submitAllWorkspacesForStudent("a1", "p1")
  }

  @Test
  fun `session messages map every field`() {
    val ended = objectMapper.readValue<SessionEndedMessage>(json)

    assertEquals(
      listOf("s1", "a1", "p1", "ENDED", "EXPIRED", "2030-03-10T13:00:00Z"),
      listOf(ended.sessionId, ended.assessmentId, ended.studentProfileId, ended.state, ended.reason, ended.endedAt),
    )

    val delivered = SessionDeliveredMessage("a1", "p1", "2030-03-10T13:00:00Z")

    assertEquals(
      """{"assessmentId":"a1","studentProfileId":"p1","deliveredAt":"2030-03-10T13:00:00Z"}""",
      objectMapper.writeValueAsString(delivered),
    )
  }
}