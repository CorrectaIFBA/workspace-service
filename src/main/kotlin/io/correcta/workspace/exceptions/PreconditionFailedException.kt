package io.correcta.workspace.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.PRECONDITION_FAILED)
class PreconditionFailedException(message: String = "A requisição não pôde ser concluída devido a uma falha na condição prévia.") : RuntimeException(message)