package io.correcta.workspace

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class HeaderAuthorizationTests(
  @Autowired val mockMvc: MockMvc,
) {

  @Test
  fun `rejects requests without identity headers`() {
    mockMvc.get("/workspaces/assessment/a1/questions/q1").andExpect { status { isUnauthorized() } }
  }
}