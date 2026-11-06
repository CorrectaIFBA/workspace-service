package io.correcta.workspace.messaging.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class Outbox(
  val outboxMessageRepository: OutboxMessageRepository,
  val objectMapper: ObjectMapper,
  val clock: Clock,
) {

  @Transactional(propagation = Propagation.MANDATORY)
  fun enqueue(exchange: String, routingKey: String, payload: Any) {
    val now = clock.instant()

    outboxMessageRepository.save(
      OutboxMessage(
        exchange = exchange,
        routingKey = routingKey,
        payload = objectMapper.writeValueAsString(payload),
        createdAt = now,
        availableAt = now,
      ),
    )
  }
}