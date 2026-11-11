package io.correcta.workspace.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** Thrown when the request carries no authenticated identity; mapped to HTTP 401. */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
class UnauthorizedException(
  override val message: String = "É necessário estar autenticado para acessar este recurso.",
) : RuntimeException(message)