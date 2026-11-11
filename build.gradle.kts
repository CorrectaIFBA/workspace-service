plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.kotlin.jpa)
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.spotless)
  jacoco
}

group = "io.correcta"
version = "0.0.1-SNAPSHOT"
description = "workspace-service"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(17)
  }
}

dependencies {
  implementation(libs.springdoc.openapi)
  implementation(libs.spring.boot.starter.actuator)
  implementation(libs.spring.boot.starter.amqp)
  implementation(libs.spring.boot.starter.aop)
  implementation(libs.spring.boot.starter.data.jpa)
  implementation(libs.spring.boot.starter.security)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.boot.starter.web)
  implementation(libs.spring.cloud.starter.openfeign)
  implementation(libs.flyway.core)
  implementation(libs.flyway.database.postgresql)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.kotlin.reflect)

  developmentOnly(libs.spring.boot.devtools)

  runtimeOnly(libs.postgresql)

  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.kotlin.test.junit5)
  testImplementation(libs.spring.security.test)
  testImplementation(libs.testcontainers.junit.jupiter)
  testImplementation(libs.testcontainers.postgresql)
  testRuntimeOnly(libs.junit.platform.launcher)
  testRuntimeOnly(libs.h2)
}

dependencyManagement {
  imports {
    mavenBom(libs.spring.cloud.dependencies.get().toString())
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict")
  }
}

allOpen {
  annotation("jakarta.persistence.Entity")
  annotation("jakarta.persistence.MappedSuperclass")
  annotation("jakarta.persistence.Embeddable")
}

val ktlintOverrides =
  mapOf(
    "ktlint_standard_no-wildcard-imports" to "disabled",
    "ktlint_standard_max-line-length" to "disabled",
    "ktlint_standard_backing-property-naming" to "disabled",
    "ktlint_standard_property-naming" to "disabled",
    "ktlint_standard_function-naming" to "disabled",
    "ktlint_standard_filename" to "disabled",
    "ktlint_standard_package-name" to "disabled",
  )

spotless {
  kotlin {
    target("src/**/*.kt")
    ktlint("1.7.1").editorConfigOverride(ktlintOverrides)
  }

  kotlinGradle {
    target("*.gradle.kts")
    ktlint("1.7.1").editorConfigOverride(ktlintOverrides)
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}

jacoco {
  toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    xml.required = true
    html.required = true
  }
}

tasks.jacocoTestCoverageVerification {
  dependsOn(tasks.jacocoTestReport)
  violationRules {
    rule {
      limit {
        counter = "LINE"
        minimum = "1.0".toBigDecimal()
      }
      limit {
        counter = "BRANCH"
        minimum = "1.0".toBigDecimal()
      }
    }
  }
}

tasks.test {
  finalizedBy(tasks.jacocoTestReport)
}

tasks.check {
  dependsOn(tasks.jacocoTestCoverageVerification)
}

val installGitHooks by tasks.registering(Exec::class) {
  commandLine("git", "config", "core.hooksPath", ".githooks")
  isIgnoreExitValue = true
}

tasks.named("compileKotlin") {
  dependsOn(installGitHooks)
}