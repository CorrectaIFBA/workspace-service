package io.correcta.workspace.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** Thrown when the request is malformed or misses required parameters; mapped to HTTP 400. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
class BadRequestException(
  override val message: String = "A requisição não pôde ser compreendida ou não possui os parâmetros obrigatórios.",
) : RuntimeException(message)