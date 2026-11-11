package io.correcta.workspace.messaging.outbox

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.correcta.workspace.service.captureArg
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutboxTest {

  private val repository = mock(OutboxMessageRepository::class.java)
  private val now = Instant.parse("2030-03-10T13:00:00Z")
  private val outbox = Outbox(repository, jacksonObjectMapper(), Clock.fixed(now, ZoneOffset.UTC))

  @Test
  fun `enqueue stores a pending message with the payload as JSON`() {
    outbox.enqueue("exchange", "key", mapOf("a" to 1))

    val captor = ArgumentCaptor.forClass(OutboxMessage::class.java)
    verify(repository).save(captor.captureArg())
    val message = captor.value

    assertEquals("exchange", message.exchange)
    assertEquals("key", message.routingKey)
    assertEquals("""{"a":1}""", message.payload)
    assertEquals(now, message.createdAt)
    assertEquals(now, message.availableAt)
    assertNull(message.publishedAt)
    assertEquals(0, message.attempts)
    assertNull(message.lastError)
  }
}