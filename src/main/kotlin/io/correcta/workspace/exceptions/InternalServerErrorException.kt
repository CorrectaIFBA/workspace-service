package io.correcta.workspace.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** Thrown when the server fails in a way the caller cannot fix; mapped to HTTP 500. */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class InternalServerErrorException(
  override val message: String = "Ocorreu um erro inesperado no servidor.",
) : RuntimeException(message)