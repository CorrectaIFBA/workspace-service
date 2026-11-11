package io.correcta.workspace.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** Thrown when the caller is authenticated but not allowed to perform the operation; mapped to HTTP 403. */
@ResponseStatus(HttpStatus.FORBIDDEN)
class ForbiddenException(
  override val message: String = "Você não tem permissão para acessar este recurso.",
) : RuntimeException(message)