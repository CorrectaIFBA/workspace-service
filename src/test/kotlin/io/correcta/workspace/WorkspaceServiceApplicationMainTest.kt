package io.correcta.workspace

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

class WorkspaceServiceApplicationMainTest {

  @Test
  fun `main boots the Spring application with the given arguments`() {
    mockStatic(SpringApplication::class.java).use { spring ->
      spring.`when`<ConfigurableApplicationContext> { SpringApplication.run(WorkspaceServiceApplication::class.java, "--server.port=0") }
        .thenReturn(mock(ConfigurableApplicationContext::class.java))

      main(arrayOf("--server.port=0"))

      spring.verify { SpringApplication.run(WorkspaceServiceApplication::class.java, "--server.port=0") }
    }
  }
}