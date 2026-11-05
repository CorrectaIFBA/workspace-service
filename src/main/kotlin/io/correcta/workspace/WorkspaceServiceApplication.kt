package io.correcta.workspace

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.EnableAspectJAutoProxy

@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = ["io.correcta.workspace.clients"])
@EnableAspectJAutoProxy
@SpringBootApplication
class WorkspaceServiceApplication

fun main(args: Array<String>) {
    runApplication<WorkspaceServiceApplication>(*args)
}
