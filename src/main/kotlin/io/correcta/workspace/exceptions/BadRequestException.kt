package io.correcta.workspace.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.BAD_REQUEST)
class BadRequestException(message: String = "A requisição não pôde ser compreendida ou não possui os parâmetros obrigatórios.") : RuntimeException(message)