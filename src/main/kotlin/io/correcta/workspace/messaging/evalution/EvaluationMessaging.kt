package io.correcta.workspace.messaging.evalution

import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarable
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/*
 * Evaluation events of evaluation-orchestrator. Every service that takes part
 * declares the whole topology (declarations are idempotent), so a message
 * published before the other side ever started is queued, not dropped.
 */
object EvaluationMessaging {
  const val EXCHANGE = "correcta.evaluations"
  const val DEAD_LETTER_EXCHANGE = "correcta.evaluations.dlx"

  const val TRIGGER_EVALUATION_KEY = "evaluation.trigger"

  const val TRIGGER_EVALUATION_QUEUE = "evaluation-orchestrator.evaluation-trigger"
}

@Configuration
class EvaluationMessagingConfig {

  @Bean
  fun evaluationMessagingTopology(): Declarables {
    val exchange = TopicExchange(EvaluationMessaging.EXCHANGE, true, false)
    val deadLetters = DirectExchange(EvaluationMessaging.DEAD_LETTER_EXCHANGE, true, false)
    val declarables = mutableListOf<Declarable>(exchange, deadLetters)

    listOf(
      EvaluationMessaging.TRIGGER_EVALUATION_QUEUE to EvaluationMessaging.TRIGGER_EVALUATION_KEY,
    ).forEach { (name, routingKey) ->
      val queue = QueueBuilder.durable(name)
        .deadLetterExchange(EvaluationMessaging.DEAD_LETTER_EXCHANGE)
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