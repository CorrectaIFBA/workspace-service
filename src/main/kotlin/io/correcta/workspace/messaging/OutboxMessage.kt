package io.correcta.workspace.messaging

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import org.hibernate.annotations.UuidGenerator

/*
 * A message waiting to reach RabbitMQ. It is written in the same transaction
 * as the change it announces, so the change and the message commit or roll
 * back together; OutboxRelay publishes it afterwards, at least once.
 */
@Entity
@Table(
    name = "outbox_messages",
    indexes = [Index(name = "idx_outbox_messages_pending", columnList = "published_at, available_at")]
)
class OutboxMessage(
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    var id: String? = null,

    @Column(nullable = false)
    var exchange: String,

    @Column(name = "routing_key", nullable = false)
    var routingKey: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var payload: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,

    @Column(name = "available_at", nullable = false)
    var availableAt: Instant,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,
)
