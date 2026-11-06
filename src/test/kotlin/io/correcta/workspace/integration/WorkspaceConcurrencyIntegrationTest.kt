package io.correcta.workspace.integration

import com.jayway.jsonpath.JsonPath
import io.correcta.workspace.clients.AssessmentClient
import io.correcta.workspace.messaging.outbox.OutboxMessageRepository
import io.correcta.workspace.messaging.outbox.OutboxRelay
import io.correcta.workspace.models.response.clients.AssessmentSessionStatusResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real PostgreSQL + Flyway migrations (V1..V3) + ddl-auto=validate, so this also proves the schema matches the entities.
 * Concurrency tests fire N requests at the same instant to exercise the cross-replica guarantees.
 * Skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceConcurrencyIntegrationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val jdbc: JdbcTemplate,
    @Autowired val outboxMessageRepository: OutboxMessageRepository,
    @Autowired val outboxRelay: OutboxRelay,
) {

    @MockitoBean
    private lateinit var assessmentClient: AssessmentClient

    private val assessmentId = "a-${UUID.randomUUID()}"

    @BeforeEach
    fun openSession() {
        `when`(assessmentClient.getSessionSubmissionStatus(anyString(), anyString()))
            .thenReturn(AssessmentSessionStatusResponse("s1", "IN_PROGRESS", null, null, acceptsSubmissions = true, late = false))
    }

    // ------------------------------------------------------------ schema

    @Test
    fun `migrations produce a schema the entities validate against`() {
        // Context startup (ddl-auto=validate) already proves it; make the check explicit on the V3 shape.
        val columns = jdbc.queryForList(
            "select column_name from information_schema.columns where table_name = 'assessment_question_submissions'",
            String::class.java,
        )

        assertEquals(true, "draft_revision_id" in columns)
        assertEquals(false, "source_code" in columns)
        assertEquals(false, "language" in columns)
        assertEquals(false, "draft_revision" in columns)
    }

    // ------------------------------------------------------------ drafts

    @Test
    fun `concurrent drafts with the same If-Match let exactly one win`() {
        val workspaceId = createWorkspace("q1")

        val statuses = inParallel(8) { i -> saveDraft(workspaceId, "\"0\"", code = "v$i") }

        assertEquals(1, statuses.count { it == 200 }, "statuses=$statuses")
        assertEquals(7, statuses.count { it == 412 }, "statuses=$statuses")
        assertEquals(1, count("select count(*) from assessment_question_draft_revisions where workspace_id = ?", workspaceId))
        assertEquals(1, count("select current_revision from assessment_question_workspaces where id = ?", workspaceId))
    }

    @Test
    fun `sequential drafts keep revisions contiguous`() {
        val workspaceId = createWorkspace("q1")

        (0 until 5).forEach { assertEquals(200, saveDraft(workspaceId, "\"$it\"", code = "v$it")) }

        assertEquals(
            listOf(1, 2, 3, 4, 5),
            jdbc.queryForList("select revision from assessment_question_draft_revisions where workspace_id = ? order by revision", Int::class.java, workspaceId),
        )
    }

    // ------------------------------------------------------------ submissions

    @Test
    fun `delivering again after a new draft supersedes the previous submission`() {
        val workspaceId = createWorkspace("q1")
        saveDraft(workspaceId, "\"0\"")
        assertEquals(202, submitAll())
        saveDraft(workspaceId, "\"1\"")

        assertEquals(202, submitAll())

        assertEquals(
            listOf("1:SUPERSEDED", "2:ACTIVE"),
            jdbc.queryForList(
                "select submission_number || ':' || status from assessment_question_submissions where workspace_id = ? order by submission_number",
                String::class.java,
                workspaceId,
            ),
        )
    }

    @Test
    fun `delivery writes a session-delivered message the relay can pick up`() {
        val workspaceId = createWorkspace("q1")
        saveDraft(workspaceId, "\"0\"")
        val before = count("select count(*) from outbox_messages where routing_key = 'session.delivered'")

        assertEquals(202, submitAll())

        assertEquals(before + 1, count("select count(*) from outbox_messages where routing_key = 'session.delivered' and published_at is null"))
        assertTrue(outboxMessageRepository.findAll().any { it.routingKey == "session.delivered" && "\"studentProfileId\":\"$PROFILE_ID\"" in it.payload })

        // The relay's PostgreSQL-only SQL (FOR UPDATE SKIP LOCKED) runs against the migrated schema: with no broker
        // reachable the row records a failed attempt; with one, it is published. Either way it was picked up.
        outboxRelay.relay()
        assertEquals(0, count("select count(*) from outbox_messages where published_at is null and attempts = 0"))
    }

    @Test
    fun `concurrent submit-all calls leave one ACTIVE submission per workspace`() {
        val w1 = createWorkspace("q1")
        val w2 = createWorkspace("q2")
        saveDraft(w1, "\"0\"")
        saveDraft(w2, "\"0\"")

        val statuses = inParallel(6) {
            mockMvc.post("/workspaces/assessment/$assessmentId/submissions") { header("X-User-Id", PROFILE_ID); header("X-User-Profile-Type", "STUDENT") }
                .andReturn().response.status
        }

        assertEquals(List(6) { 202 }, statuses, "statuses=$statuses")
        listOf(w1, w2).forEach {
            assertEquals(1, count("select count(*) from assessment_question_submissions where workspace_id = ? and status = 'ACTIVE'", it))
            assertEquals(1, count("select count(*) from assessment_question_submissions where workspace_id = ?", it))
        }
    }

    @Test
    fun `concurrent first access creates a single workspace`() {
        val statuses = inParallel(6) {
            mockMvc.get("/workspaces/assessment/$assessmentId/questions/q-race") { header("X-User-Id", PROFILE_ID); header("X-User-Profile-Type", "STUDENT") }
                .andReturn().response.status
        }

        assertEquals(List(6) { 200 }, statuses, "statuses=$statuses")
        assertEquals(
            1,
            count("select count(*) from assessment_question_workspaces where assessment_id = ? and question_id = 'q-race'", assessmentId),
        )
    }

    // ------------------------------------------------------------ helpers

    private fun createWorkspace(questionId: String): String {
        val body = mockMvc.get("/workspaces/assessment/$assessmentId/questions/$questionId") { header("X-User-Id", PROFILE_ID); header("X-User-Profile-Type", "STUDENT") }
            .andReturn().response.contentAsString

        return JsonPath.read(body, "$.id")
    }

    private fun saveDraft(workspaceId: String, ifMatch: String, code: String = "int main() { return 0; }"): Int =
        mockMvc.put("/workspaces/$workspaceId/drafts") {
            header("X-User-Id", PROFILE_ID); header("X-User-Profile-Type", "STUDENT")
            header("If-Match", ifMatch)
            contentType = MediaType.APPLICATION_JSON
            content = """{"sourceCode":"$code","language":"C"}"""
        }.andReturn().response.status

    private fun submitAll(): Int =
        mockMvc.post("/workspaces/assessment/$assessmentId/submissions") {
            header("X-User-Id", PROFILE_ID); header("X-User-Profile-Type", "STUDENT")
        }.andReturn().response.status

    private fun count(sql: String, vararg args: Any): Int = jdbc.queryForObject(sql, Int::class.java, *args)!!

    /** Runs [task] on [threads] threads released by a barrier at the same moment. */
    private fun <T> inParallel(threads: Int, task: (Int) -> T): List<T> {
        val pool = Executors.newFixedThreadPool(threads)
        val barrier = CyclicBarrier(threads)

        try {
            val futures = (0 until threads).map { i ->
                pool.submit<T> {
                    barrier.await()
                    task(i)
                }
            }

            return futures.map { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }

    companion object {
        private const val PROFILE_ID = "student-1"

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("clients.assessment-service.url") { "http://127.0.0.1:0" }
        }
    }
}
