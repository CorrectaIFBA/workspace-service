package io.correcta.workspace.handlers

import io.correcta.workspace.exceptions.BadRequestException
import io.correcta.workspace.exceptions.ConflictException
import io.correcta.workspace.exceptions.ForbiddenException
import io.correcta.workspace.exceptions.InternalServerErrorException
import io.correcta.workspace.exceptions.NotFoundException
import io.correcta.workspace.exceptions.PreconditionFailedException
import io.correcta.workspace.exceptions.UnauthorizedException
import io.correcta.workspace.models.response.error.ErrorResponse
import io.correcta.workspace.models.response.error.ValidationErrorResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Translates exceptions into JSON error bodies with Portuguese messages for the client.
 * Domain exceptions keep their own message; framework exceptions get a fixed one.
 */
@RestControllerAdvice
class CustomExceptionHandler {

  private val log: Logger = LoggerFactory.getLogger(CustomExceptionHandler::class.java)

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleMethodArgumentNotValidException(exception: MethodArgumentNotValidException): ResponseEntity<ValidationErrorResponse> {
    val fieldErrors = exception.bindingResult.fieldErrors
    val validationErrorResponse =
      ValidationErrorResponse(
        message = "A validação dos parâmetros da requisição falhou.",
        errors = fieldErrors.associate { it.field to (it.defaultMessage ?: "Valor inválido") },
      )

    log.warn("Validation errors : {} ; Parameters : {}", validationErrorResponse.errors, exception.bindingResult.target)

    return ResponseEntity.status(400)
      .body(validationErrorResponse)
  }

  @ExceptionHandler(BadRequestException::class)
  fun handleBadRequestException(exception: BadRequestException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(400)
    .body(ErrorResponse(exception.message))

  @ExceptionHandler(UnauthorizedException::class)
  fun handleUnauthorizedException(exception: UnauthorizedException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(401)
    .body(ErrorResponse(exception.message))

  @ExceptionHandler(ForbiddenException::class)
  fun handleForbiddenException(exception: ForbiddenException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(403)
    .body(ErrorResponse(exception.message))

  @ExceptionHandler(AuthorizationDeniedException::class)
  fun handleAuthorizationDeniedException(): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(403)
    .body(ErrorResponse("Você não tem permissão para acessar este recurso."))

  @ExceptionHandler(NoResourceFoundException::class)
  fun handleNoResourceFoundException(exception: NoResourceFoundException): ResponseEntity<ErrorResponse> {
    log.warn("No handler for {}", exception.resourcePath)

    return ResponseEntity
      .status(404)
      .body(ErrorResponse("O recurso solicitado não foi encontrado."))
  }

  @ExceptionHandler(NotFoundException::class)
  fun handleNotFoundException(exception: NotFoundException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(404)
    .body(ErrorResponse(exception.message))

  @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
  fun handleMethodNotSupportedException(exception: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
    val supported = exception.supportedMethods?.joinToString(", ").orEmpty()

    log.warn("Method {} is not supported for this endpoint (supported: {})", exception.method, supported)

    val response = ResponseEntity.status(405)

    // RFC 9110 requires a 405 to advertise what the endpoint does accept.
    if (supported.isNotEmpty()) response.header("Allow", supported)

    return response.body(ErrorResponse("O método '${exception.method}' não é suportado por este endpoint."))
  }

  @ExceptionHandler(ConflictException::class)
  fun handleConflictException(exception: ConflictException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(409)
    .body(ErrorResponse(exception.message))

  @ExceptionHandler(MissingRequestHeaderException::class)
  fun handleMissingRequestHeaderException(exception: MissingRequestHeaderException): ResponseEntity<ErrorResponse> {
    // RFC 6585: a required precondition header is missing -> 428.
    if (exception.headerName.equals("If-Match", ignoreCase = true)) {
      return ResponseEntity
        .status(428)
        .body(ErrorResponse("O cabeçalho If-Match é obrigatório para esta operação."))
    }

    return ResponseEntity
      .status(400)
      .body(ErrorResponse("O cabeçalho '${exception.headerName}' é obrigatório."))
  }

  @ExceptionHandler(PreconditionFailedException::class)
  fun handlePreconditionFailedException(exception: PreconditionFailedException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(412)
    .body(ErrorResponse(exception.message))

  @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
  fun handleMediaTypeNotSupportedException(exception: HttpMediaTypeNotSupportedException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(415)
    .body(ErrorResponse("O tipo de conteúdo '${exception.contentType}' não é suportado por este endpoint."))

  @ExceptionHandler(InternalServerErrorException::class)
  fun handleInternalServerErrorException(exception: InternalServerErrorException): ResponseEntity<ErrorResponse> {
    log.error("Internal server error: ", exception)

    return ResponseEntity
      .status(500)
      .body(ErrorResponse("Ocorreu um erro interno no servidor. Por favor, tente novamente mais tarde."))
  }

  @ExceptionHandler(Exception::class)
  fun handleGenericException(exception: Exception): ResponseEntity<ErrorResponse> {
    log.error("An unexpected error occurred: ", exception)

    return ResponseEntity
      .status(500)
      .body(ErrorResponse("Ocorreu um erro inesperado. Por favor, tente novamente mais tarde."))
  }
}