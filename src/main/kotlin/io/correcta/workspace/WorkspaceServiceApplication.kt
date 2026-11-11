package io.correcta.workspace

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.EnableAspectJAutoProxy

/** Spring Boot entry point of workspace-service. */
@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = ["io.correcta.workspace.clients"])
@EnableAspectJAutoProxy
@SpringBootApplication
class WorkspaceServiceApplication

/** Starts the application. */
fun main(args: Array<String>) {
  runApplication<WorkspaceServiceApplication>(*args)
}