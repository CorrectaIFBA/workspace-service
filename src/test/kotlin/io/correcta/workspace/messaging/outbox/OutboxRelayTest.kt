package io.correcta.workspace.messaging.outbox

import io.correcta.workspace.service.anyK
import io.correcta.workspace.service.anyStr
import io.correcta.workspace.service.captureArg
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageDeliveryMode
import org.springframework.amqp.rabbit.core.RabbitOperations
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class OutboxRelayTest {

  private val now = Instant.parse("2030-03-10T13:00:00Z")
  private val jdbc = mock(JdbcTemplate::class.java)
  private val rabbit = mock(RabbitTemplate::class.java)
  private val operations = mock(RabbitOperations::class.java)
  private val transactionTemplate = mock(TransactionTemplate::class.java)
  private val relay = OutboxRelay(jdbc, rabbit, transactionTemplate, Clock.fixed(now, ZoneOffset.UTC))

  /** Rows returned by successive polls; an exhausted queue returns an empty batch. */
  private val polls = ArrayDeque<List<Row>>()

  private data class Row(val id: String, val routingKey: String = "key", val attempts: Int = 0)

  @BeforeEach
  fun setUp() {
    `when`(transactionTemplate.execute(anyK<TransactionCallback<Int>>())).thenAnswer {
      it.getArgument<TransactionCallback<Int>>(0).doInTransaction(SimpleTransactionStatus())
    }
    `when`(rabbit.invoke(anyK<RabbitOperations.OperationsCallback<Any>>())).thenAnswer {
      it.getArgument<RabbitOperations.OperationsCallback<Any>>(0).doInRabbit(operations)
    }
    `when`(jdbc.query(anyStr(), anyK<RowMapper<Any>>(), anyK<Any>(), anyK<Any>())).thenAnswer { invocation ->
      val mapper = invocation.getArgument<RowMapper<Any>>(1)
      polls.removeFirstOrNull().orEmpty().mapIndexed { index, row -> mapper.mapRow(resultSet(row), index) }
    }
  }

  @Test
  fun `an empty outbox publishes nothing`() {
    relay.relay()

    verify(operations, never()).send(anyStr(), anyStr(), anyK<Message>())
    verify(jdbc, never()).update(anyStr(), anyK<Any>(), anyK<Any>())
  }

  @Test
  fun `a confirmed message is published as persistent JSON and marked published`() {
    polls.add(listOf(Row("m1", routingKey = "session.delivered")))

    relay.relay()

    val captor = ArgumentCaptor.forClass(Message::class.java)
    verify(operations).send(anyStr(), anyStr(), captor.captureArg())
    val message = captor.value
    assertEquals("""{"id":"m1"}""", String(message.body))
    assertEquals("m1", message.messageProperties.messageId)
    assertEquals(MessageDeliveryMode.PERSISTENT, message.messageProperties.deliveryMode)
    verify(operations).waitForConfirmsOrDie(5_000L)
    verify(jdbc).update("UPDATE outbox_messages SET published_at = ? WHERE id = ?", Timestamp.from(now), "m1")
  }

  @Test
  fun `a full batch triggers another poll in the same run`() {
    polls.add((1..50).map { Row("m$it") })
    polls.add(listOf(Row("m51")))

    relay.relay()

    verify(operations, times(51)).send(anyStr(), anyStr(), anyK<Message>())
  }

  @Test
  fun `a failed publish records the error and backs off exponentially`() {
    polls.add(listOf(Row("m1", attempts = 2)))
    doThrow(AmqpException("nack")).`when`(operations).waitForConfirmsOrDie(anyLong())

    relay.relay()

    verify(jdbc).update(
      "UPDATE outbox_messages SET attempts = ?, last_error = ?, available_at = ? WHERE id = ?",
      3,
      "nack",
      Timestamp.from(now.plus(Duration.ofSeconds(8))),
      "m1",
    )
  }

  @Test
  fun `the backoff is capped at five minutes and errors without a message are stored as null`() {
    polls.add((1..50).map { Row("m$it", attempts = 20) })
    doThrow(RuntimeException()).`when`(operations).waitForConfirmsOrDie(anyLong())

    relay.relay()

    verify(jdbc).update(
      "UPDATE outbox_messages SET attempts = ?, last_error = ?, available_at = ? WHERE id = ?",
      21,
      null,
      Timestamp.from(now.plus(Duration.ofMinutes(5))),
      "m1",
    )
    // Nothing was published, so a full batch of failures does not poll again.
    verify(jdbc, times(1)).query(anyStr(), anyK<RowMapper<Any>>(), anyK<Any>(), anyK<Any>())
  }

  @Test
  fun `a transaction that yields no result stops the run`() {
    doReturn(null).`when`(transactionTemplate).execute(anyK<TransactionCallback<Int>>())

    relay.relay()

    verify(jdbc, never()).query(anyStr(), anyK<RowMapper<Any>>(), anyK<Any>(), anyK<Any>())
  }

  private fun resultSet(row: Row): ResultSet {
    val resultSet = mock(ResultSet::class.java)
    `when`(resultSet.getString("id")).thenReturn(row.id)
    `when`(resultSet.getString("exchange")).thenReturn("exchange")
    `when`(resultSet.getString("routing_key")).thenReturn(row.routingKey)
    `when`(resultSet.getString("payload")).thenReturn("""{"id":"${row.id}"}""")
    `when`(resultSet.getInt("attempts")).thenReturn(row.attempts)
    return resultSet
  }
}