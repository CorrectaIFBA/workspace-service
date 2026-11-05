package io.correcta.workspace.messaging

import org.springframework.data.jpa.repository.JpaRepository

interface OutboxMessageRepository : JpaRepository<OutboxMessage, String>
