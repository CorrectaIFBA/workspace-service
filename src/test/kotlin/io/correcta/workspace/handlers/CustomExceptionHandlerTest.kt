package io.correcta.workspace.handlers

import io.correcta.workspace.exceptions.BadRequestException
import io.correcta.workspace.exceptions.ConflictException
import io.correcta.workspace.exceptions.ForbiddenException
import io.correcta.workspace.exceptions.InternalServerErrorException
import io.correcta.workspace.exceptions.NotFoundException
import io.correcta.workspace.exceptions.PreconditionFailedException
import io.correcta.workspace.exceptions.UnauthorizedException
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.servlet.resource.NoResourceFoundException
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomExceptionHandlerTest {

  private val handler = CustomExceptionHandler()

  private val parameter = MethodParameter(String::class.java.getMethod("concat", String::class.java), 0)

  @Test
  fun `validation errors return 400 with one message per field`() {
    val bindingResult = BeanPropertyBindingResult(Any(), "request")
    bindingResult.addError(FieldError("request", "sourceCode", "O código-fonte é obrigatório"))
    bindingResult.addError(FieldError("request", "language", null, false, null, null, null))

    val response = handler.handleMethodArgumentNotValidException(MethodArgumentNotValidException(parameter, bindingResult))

    assertEquals(400, response.statusCode.value())
    assertEquals(
      mapOf("sourceCode" to "O código-fonte é obrigatório", "language" to "Valor inválido"),
      response.body!!.errors,
    )
  }

  @Test
  fun `domain exceptions keep their status and message`() {
    val cases = listOf(
      handler.handleBadRequestException(BadRequestException("bad")) to (400 to "bad"),
      handler.handleUnauthorizedException(UnauthorizedException("anon")) to (401 to "anon"),
      handler.handleForbiddenException(ForbiddenException("no")) to (403 to "no"),
      handler.handleNotFoundException(NotFoundException("gone")) to (404 to "gone"),
      handler.handleConflictException(ConflictException("clash")) to (409 to "clash"),
      handler.handlePreconditionFailedException(PreconditionFailedException("stale")) to (412 to "stale"),
    )

    cases.forEach { (response, expected) ->
      assertEquals(expected.first, response.statusCode.value())
      assertEquals(expected.second, response.body!!.message)
    }
  }

  @Test
  fun `authorization denied returns 403`() {
    assertEquals(403, handler.handleAuthorizationDeniedException().statusCode.value())
  }

  @Test
  fun `unknown resource returns 404`() {
    val response = handler.handleNoResourceFoundException(NoResourceFoundException(HttpMethod.GET, "/missing"))

    assertEquals(404, response.statusCode.value())
  }

  @Test
  fun `unsupported method returns 405 and advertises the allowed methods`() {
    val response = handler.handleMethodNotSupportedException(HttpRequestMethodNotSupportedException("DELETE", listOf("GET", "PUT")))

    assertEquals(405, response.statusCode.value())
    assertEquals("GET, PUT", response.headers.getFirst("Allow"))
    assertEquals("O método 'DELETE' não é suportado por este endpoint.", response.body!!.message)
  }

  @Test
  fun `unsupported method without known alternatives omits the Allow header`() {
    val response = handler.handleMethodNotSupportedException(HttpRequestMethodNotSupportedException("DELETE"))

    assertEquals(405, response.statusCode.value())
    assertNull(response.headers.getFirst("Allow"))
  }

  @Test
  fun `missing If-Match returns 428`() {
    val response = handler.handleMissingRequestHeaderException(MissingRequestHeaderException("If-Match", parameter))

    assertEquals(428, response.statusCode.value())
  }

  @Test
  fun `any other missing header returns 400`() {
    val response = handler.handleMissingRequestHeaderException(MissingRequestHeaderException("X-Other", parameter))

    assertEquals(400, response.statusCode.value())
    assertEquals("O cabeçalho 'X-Other' é obrigatório.", response.body!!.message)
  }

  @Test
  fun `unsupported media type returns 415`() {
    val exception = HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, listOf(MediaType.APPLICATION_JSON))

    val response = handler.handleMediaTypeNotSupportedException(exception)

    assertEquals(415, response.statusCode.value())
    assertEquals("O tipo de conteúdo 'text/plain' não é suportado por este endpoint.", response.body!!.message)
  }

  @Test
  fun `internal errors return 500 without leaking the cause`() {
    val response = handler.handleInternalServerErrorException(InternalServerErrorException("db password leaked"))

    assertEquals(500, response.statusCode.value())
    assertEquals("Ocorreu um erro interno no servidor. Por favor, tente novamente mais tarde.", response.body!!.message)
  }

  @Test
  fun `unexpected exceptions return 500`() {
    val response = handler.handleGenericException(IllegalStateException("boom"))

    assertEquals(500, response.statusCode.value())
    assertEquals("Ocorreu um erro inesperado. Por favor, tente novamente mais tarde.", response.body!!.message)
  }
}