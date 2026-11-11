package io.correcta.workspace.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** Thrown when the resource does not exist or is not visible to the caller; mapped to HTTP 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
class NotFoundException(
  override val message: String = "O recurso solicitado não foi encontrado.",
) : RuntimeException(message)