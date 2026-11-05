package io.correcta.workspace.models

import io.correcta.workspace.models.request.MAX_SOURCE_CODE_LENGTH
import io.correcta.workspace.models.request.SaveDraftRevisionRequest
import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveDraftRevisionRequestTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    private fun violations(request: SaveDraftRevisionRequest) =
        validator.validate(request).map { it.propertyPath.toString() }

    @Test
    fun `accepts code at the size limit`() {
        val request = SaveDraftRevisionRequest("a".repeat(MAX_SOURCE_CODE_LENGTH), "C")

        assertTrue(violations(request).isEmpty())
    }

    @Test
    fun `rejects code above the size limit`() {
        val request = SaveDraftRevisionRequest("a".repeat(MAX_SOURCE_CODE_LENGTH + 1), "C")

        assertEquals(listOf("sourceCode"), violations(request))
    }

    @Test
    fun `rejects blank code and blank language`() {
        val request = SaveDraftRevisionRequest(" ", "")

        assertEquals(setOf("sourceCode", "language"), violations(request).toSet())
    }
}
