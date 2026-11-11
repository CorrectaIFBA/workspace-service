package io.correcta.workspace.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/** Enables JPA auditing, which fills the `@CreatedDate` and `@LastModifiedDate` columns. */
@Configuration
@EnableJpaAuditing
class JpaConfig