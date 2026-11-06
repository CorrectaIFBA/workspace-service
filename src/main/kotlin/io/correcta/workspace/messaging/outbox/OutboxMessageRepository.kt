package io.correcta.workspace.messaging.outbox

import org.springframework.data.jpa.repository.JpaRepository

interface OutboxMessageRepository : JpaRepository<OutboxMessage, String>