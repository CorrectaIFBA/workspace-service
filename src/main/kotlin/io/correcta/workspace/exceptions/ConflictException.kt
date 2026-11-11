package io.correcta.workspace.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** Thrown when the request conflicts with the current state of the resource; mapped to HTTP 409. */
@ResponseStatus(HttpStatus.CONFLICT)
class ConflictException(
  override val message: String = "A requisição não pôde ser concluída devido a um conflito com o estado atual do recurso.",
) : RuntimeException(message)