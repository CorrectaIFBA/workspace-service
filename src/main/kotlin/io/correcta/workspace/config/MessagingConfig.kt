package io.correcta.workspace.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

/* Scheduling runs the outbox relay; the clock stamps outbox rows. */
@Configuration
@EnableScheduling
class MessagingConfig {

  @Bean
  fun clock(): Clock = Clock.systemUTC()
}