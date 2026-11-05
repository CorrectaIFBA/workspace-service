package io.correcta.workspace.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.CONFLICT)
class ConflictException(message: String = "A requisição não pôde ser concluída devido a um conflito com o estado atual do recurso.") : RuntimeException(message)