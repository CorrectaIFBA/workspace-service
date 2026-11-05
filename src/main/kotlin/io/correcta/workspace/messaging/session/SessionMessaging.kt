package io.correcta.workspace.messaging.session

import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarable
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/*
 * Assessment sessions between assessment-service and workspace-service.
 * Both services declare the whole topology (declarations are idempotent),
 * so a message published before the other side ever started is queued,
 * not dropped as unroutable.
 *
 *   session.ended      assessment-service -> workspace-service
 *                      The session finished without the student delivering it
 *                      (deadline, archive, faculty close): submit the latest drafts.
 *   session.delivered  workspace-service -> assessment-service
 *                      The student delivered and every workspace is submitted:
 *                      mark the session SUBMITTED.
 *
 * A message that keeps failing after the listener's retries goes to <queue>.dlq.
 */
object SessionMessaging {
    const val EXCHANGE = "correcta.assessment-sessions"
    const val DEAD_LETTER_EXCHANGE = "correcta.assessment-sessions.dlx"

    const val SESSION_ENDED_KEY = "session.ended"
    const val SESSION_DELIVERED_KEY = "session.delivered"

    const val SESSION_ENDED_QUEUE = "workspace-service.session-ended"
    const val SESSION_DELIVERED_QUEUE = "assessment-service.session-delivered"
}

data class SessionEndedMessage(
    val sessionId: String,
    val assessmentId: String,
    val studentProfileId: String,
    val state: String,
    /* EXPIRED, CLOSED_BY_ARCHIVE or CLOSED_BY_FACULTY. */
    val reason: String,
    val endedAt: String,
)

data class SessionDeliveredMessage(
    val assessmentId: String,
    val studentProfileId: String,
    val deliveredAt: String,
)

@Configuration
class SessionMessagingConfig {

    @Bean
    fun sessionMessagingTopology(): Declarables {
        val exchange = TopicExchange(SessionMessaging.EXCHANGE, true, false)
        val deadLetters = DirectExchange(SessionMessaging.DEAD_LETTER_EXCHANGE, true, false)
        val declarables = mutableListOf<Declarable>(exchange, deadLetters)

        listOf(
            SessionMessaging.SESSION_ENDED_QUEUE to SessionMessaging.SESSION_ENDED_KEY,
            SessionMessaging.SESSION_DELIVERED_QUEUE to SessionMessaging.SESSION_DELIVERED_KEY,
        ).forEach { (name, routingKey) ->
            val queue = QueueBuilder.durable(name)
                .deadLetterExchange(SessionMessaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(name)
                .build()
            val deadLetterQueue = QueueBuilder.durable("$name.dlq").build()

            declarables += queue
            declarables += deadLetterQueue
            declarables += BindingBuilder.bind(queue).to(exchange).with(routingKey)
            declarables += BindingBuilder.bind(deadLetterQueue).to(deadLetters).with(name)
        }

        return Declarables(declarables)
    }
}
