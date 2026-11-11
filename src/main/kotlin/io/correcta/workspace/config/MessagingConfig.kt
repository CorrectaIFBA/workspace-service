package io.correcta.workspace.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

/** Enables scheduling for the outbox relay and exposes the UTC [Clock] used to stamp outbox rows. */
@Configuration
@EnableScheduling
class MessagingConfig {

  @Bean
  fun clock(): Clock = Clock.systemUTC()
}