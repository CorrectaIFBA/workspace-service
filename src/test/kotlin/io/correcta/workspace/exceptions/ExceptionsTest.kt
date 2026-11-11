package io.correcta.workspace.exceptions

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus
import kotlin.test.assertEquals

class ExceptionsTest {

  @Test
  fun `every exception has a default message and its HTTP status`() {
    val cases = listOf(
      BadRequestException() to HttpStatus.BAD_REQUEST,
      UnauthorizedException() to HttpStatus.UNAUTHORIZED,
      ForbiddenException() to HttpStatus.FORBIDDEN,
      NotFoundException() to HttpStatus.NOT_FOUND,
      ConflictException() to HttpStatus.CONFLICT,
      PreconditionFailedException() to HttpStatus.PRECONDITION_FAILED,
      InternalServerErrorException() to HttpStatus.INTERNAL_SERVER_ERROR,
    )

    cases.forEach { (exception, status) ->
      assertEquals(true, exception.message!!.isNotBlank(), exception::class.simpleName)
      assertEquals(status, exception::class.java.getAnnotation(ResponseStatus::class.java).value)
    }
  }

  @Test
  fun `a custom message replaces the default`() {
    assertEquals("custom", ConflictException("custom").message)
  }
}