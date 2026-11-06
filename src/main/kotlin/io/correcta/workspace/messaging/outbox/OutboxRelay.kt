package io.correcta.workspace.messaging.outbox

import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageDeliveryMode
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/*
 * Publishes the outbox to RabbitMQ. A row is marked published only after the
 * broker confirms it; a crash in between publishes it again, so consumers
 * must be idempotent. Failed rows back off exponentially, up to 5 minutes.
 *
 * Plain JDBC on purpose: the relay polls every second, and through Hibernate
 * each poll would be printed whenever spring.jpa.show-sql is on.
 */
@Component
class OutboxRelay(
    val jdbcTemplate: JdbcTemplate,
    val rabbitTemplate: RabbitTemplate,
    val transactionTemplate: TransactionTemplate,
    val clock: Clock,
) {

    private val log: Logger = LoggerFactory.getLogger(OutboxRelay::class.java)

    private class Pending(val id: String, val exchange: String, val routingKey: String, val payload: String, val attempts: Int)

    @Scheduled(
        fixedDelayString = "\${outbox.relay-interval-ms:1000}",
        initialDelayString = "\${outbox.relay-interval-ms:1000}"
    )
    fun relay() {
        while (relayBatch() == BATCH_SIZE) {
            // a full batch may mean more are waiting.
        }
    }

    private fun relayBatch(): Int = transactionTemplate.execute {
        val now = clock.instant()

        val batch = jdbcTemplate.query(
            """
            SELECT id, exchange, routing_key, payload, attempts FROM outbox_messages
            WHERE published_at IS NULL AND available_at <= ?
            ORDER BY created_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """,
            { row, _ ->
                Pending(row.getString("id"), row.getString("exchange"), row.getString("routing_key"), row.getString("payload"), row.getInt("attempts"))
            },
            Timestamp.from(now),
            BATCH_SIZE,
        )
        var published = 0

        for (message in batch) {
            try {
                publish(message)
                jdbcTemplate.update("UPDATE outbox_messages SET published_at = ? WHERE id = ?", Timestamp.from(clock.instant()), message.id)
                published++
            } catch (exception: RuntimeException) {
                val attempts = message.attempts + 1
                jdbcTemplate.update(
                    "UPDATE outbox_messages SET attempts = ?, last_error = ?, available_at = ? WHERE id = ?",
                    attempts,
                    exception.message?.take(2000),
                    Timestamp.from(now.plus(backoff(attempts))),
                    message.id,
                )

                log.warn("Failed to publish outbox message {} (attempt {}).", message.id, attempts, exception)
            }
        }

        if (published > 0) {
            log.info("Published {} outbox message(s): {}.", published, batch.map { it.routingKey }.distinct().joinToString())
        }

        if (published == 0) 0 else batch.size
    } ?: 0

    private fun publish(pending: Pending) {
        val properties = MessageProperties().apply {
            contentType = MessageProperties.CONTENT_TYPE_JSON
            contentEncoding = "UTF-8"
            messageId = pending.id
            deliveryMode = MessageDeliveryMode.PERSISTENT
        }
        val message = Message(pending.payload.toByteArray(Charsets.UTF_8), properties)

        rabbitTemplate.invoke { operations ->
            operations.send(pending.exchange, pending.routingKey, message)
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS)
        }
    }

    private fun backoff(attempts: Int): Duration =
        Duration.ofSeconds(minOf(300L, 1L shl minOf(attempts, 9)))

    companion object {
        private const val BATCH_SIZE = 50
        private const val CONFIRM_TIMEOUT_MS = 5_000L
    }
}
