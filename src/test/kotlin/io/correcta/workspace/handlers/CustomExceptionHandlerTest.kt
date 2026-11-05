package io.correcta.workspace.handlers

import io.correcta.workspace.exceptions.PreconditionFailedException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CustomExceptionHandlerTest {

    private val handler = CustomExceptionHandler()

    @Test
    fun `maps PreconditionFailedException to 412 instead of the generic 500`() {
        val response = handler.handlePreconditionFailedException(PreconditionFailedException("stale"))

        assertEquals(412, response.statusCode.value())
    }
}
