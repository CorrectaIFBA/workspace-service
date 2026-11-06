package io.correcta.workspace.messaging.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.correcta.workspace.service.SubmissionService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * Consumes `session.ended`: when a session finishes without the student delivering it,
 * submits the latest draft of each of their workspaces. Ignores the deadline because the session already ended.
 */
@Component
class SessionEndedListener(
  val submissionService: SubmissionService,
  val objectMapper: ObjectMapper,
) {

  private val log: Logger = LoggerFactory.getLogger(SessionEndedListener::class.java)

  @RabbitListener(queues = [SessionMessaging.SESSION_ENDED_QUEUE])
  fun onSessionEnded(message: Message) {
    val ended = objectMapper.readValue<SessionEndedMessage>(message.body)

    log.info(
      "Session {} ended ({}); submitting the drafts of student {} in assessment {}.",
      ended.sessionId,
      ended.reason,
      ended.studentProfileId,
      ended.assessmentId,
    )

    submissionService.submitAllWorkspacesForStudent(ended.assessmentId, ended.studentProfileId)
  }
}