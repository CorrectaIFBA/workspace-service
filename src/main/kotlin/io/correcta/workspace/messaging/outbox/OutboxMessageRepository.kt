package io.correcta.workspace.messaging.outbox

import org.springframework.data.jpa.repository.JpaRepository

/** Persistence of [OutboxMessage]; the relay reads pending rows with plain JDBC instead. */
interface OutboxMessageRepository : JpaRepository<OutboxMessage, String>