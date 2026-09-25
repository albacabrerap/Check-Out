package com.checkout.backend.exceptions;

import com.checkout.backend.exceptions.dto.ErrorResponseDTO;
import com.checkout.backend.exceptions.dto.ErrorResponseDTO.FieldErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Translates any exception of a controller to a JSON response with the ErrorResponseDTO structure.

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_ERROR_MESSAGE =
            "Ocurrio un error interno. Intentalo de nuevo mas tarde.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponseDTO> handleApiException(ApiException ex,
                                                               HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    // 400 - el request no es utilizable
        // @Valid fails over a @RequestBody (400).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        List<FieldErrorDTO> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();

        log.debug("Validacion fallida en {}: {}", request.getRequestURI(), fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDTO.of(
                        HttpStatus.BAD_REQUEST,
                        "La solicitud contiene campos invalidos.",
                        request.getRequestURI(),
                        fieldErrors));
    }

    // A restriction declared on a parameter of the controller fails.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodValidation(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        List<FieldErrorDTO> fieldErrors = ex.getParameterValidationResults().stream()
                .flatMap(result -> toFieldErrors(result).stream())
                .toList();

        log.debug("Validacion de parametros fallida en {}: {}", request.getRequestURI(), fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDTO.of(
                        HttpStatus.BAD_REQUEST,
                        "La solicitud contiene parametros invalidos.",
                        request.getRequestURI(),
                        fieldErrors));
    }

    // A restriction declared outside of @RequestBody fails.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        List<FieldErrorDTO> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDTO(
                        lastNode(String.valueOf(violation.getPropertyPath())),
                        violation.getMessage()))
                .toList();

        log.debug("Restriccion violada en {}: {}", request.getRequestURI(), fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDTO.of(
                        HttpStatus.BAD_REQUEST,
                        "La solicitud contiene parametros invalidos.",
                        request.getRequestURI(),
                        fieldErrors));
    }

    // The body wasn't able to deserialize.
    // Message includes destined Java class and the stream position, they describe the internal structure.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        log.debug("Cuerpo ilegible en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no se pudo leer. Revisa que sea JSON valido.", request);
    }

    // A required @RequestParam is missing.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Falta el parametro obligatorio '" + ex.getParameterName() + "'.", request);
    }

    // A required @RequestHeader is missing.
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingHeader(MissingRequestHeaderException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Falta la cabecera obligatoria '" + ex.getHeaderName() + "'.", request);
    }

    // Manages the rest of the possible missing values of the request.
    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingRequestValue(MissingRequestValueException ex,
                                                                      HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Falta un valor obligatorio en la solicitud.", request);
    }

    // Missing path variable declared on method (500).
    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingPathVariable(MissingPathVariableException ex,
                                                                      HttpServletRequest request) {
        log.error("Mapping invalido en {}: falta la path variable '{}' en la plantilla de la URI",
                request.getRequestURI(), ex.getVariableName(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, request);
    }

    // Parameter with a non-convertable datatype
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        Class<?> expected = ex.getRequiredType();
        String message = "El valor '" + ex.getValue() + "' no es valido para el parametro '"
                + ex.getName() + "'"
                + (expected != null ? ", se esperaba " + expected.getSimpleName() : "")
                + ".";
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // 401 / 403 - security
    // Authentication on controller fails.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthentication(AuthenticationException ex,
                                                                 HttpServletRequest request) {
        log.debug("Autenticacion fallida en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Credenciales invalidas.", request);
    }

    // Authenticated user without permissions (403).
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        if (!isAuthenticated()) {
            log.debug("Acceso anonimo rechazado en {}", request.getRequestURI());
            return build(HttpStatus.UNAUTHORIZED,
                    "Debes autenticarte para acceder a este recurso.", request);
        }
        log.debug("Acceso denegado en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta accion.", request);
    }

    // 404 / 405 - path

    // No handler for the requested URL (404).
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoResourceFound(NoResourceFoundException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "La ruta solicitada no existe.", request);
    }

    // No handler or resource handler to take the URL.
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoHandlerFound(NoHandlerFoundException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "La ruta solicitada no existe.", request);
    }

    // Non-existant-path-ish
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                     HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "El metodo " + ex.getMethod() + " no esta permitido en esta ruta.", request);
    }

    // 406 / 415 - content negociation

    // The body Content-Type isn't readable for the API
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                        HttpServletRequest request) {
        String supported = String.join(", ", ex.getSupportedMediaTypes().stream()
                .map(Object::toString)
                .toList());
        String message = "El tipo de contenido '" + ex.getContentType() + "' no esta soportado."
                + (supported.isEmpty() ? "" : " Se aceptan: " + supported + ".");
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message, request);
    }

    // Client requests a format the API does not produce.
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponseDTO> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                                         HttpServletRequest request) {
        return build(HttpStatus.NOT_ACCEPTABLE,
                "Esta API solo responde en formato JSON.", request);
    }

    // 409 - conflicto con el estado guardado

    // Constraint skip (UNIQUE/FK) -> WARN/DEBUG
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                HttpServletRequest request) {
        log.warn("Violacion de integridad en {}", request.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT,
                "La operacion entra en conflicto con datos ya registrados.", request);
    }

    // 502 / 503 - email

    // EmailService rejects the request (502).
    @ExceptionHandler(EmailSenderException.class)
    public ResponseEntity<ErrorResponseDTO> handleEmailSender(EmailSenderException ex,
                                                              HttpServletRequest request) {
        log.error("Fallo el envio de correo en {}", request.getRequestURI(), ex);
        return build(HttpStatus.BAD_GATEWAY,
                "No se pudo enviar el correo en este momento.", request);
    }

    // The executer's email queue is full (503) -> WARN.
    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<ErrorResponseDTO> handleTaskRejected(TaskRejectedException ex,
                                                               HttpServletRequest request) {
        log.warn("Cola de tareas llena en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio esta ocupado. Intentalo de nuevo en unos minutos.", request);
    }

    // Exception declared runaway
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDTO> handleResponseStatus(ResponseStatusException ex,
                                                                 HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            // No reason phrase for the DTO.
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (status.is5xxServerError()) {
            log.error("Error con status propio en {} {}", request.getMethod(), request.getRequestURI(), ex);
            return build(status, INTERNAL_ERROR_MESSAGE, request);
        }

        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        log.debug("Status propio {} en {}: {}", status.value(), request.getRequestURI(), message);
        return build(status, message, request);
    }

    // 500 - rest

    // Any exception unmatched with a handler.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex,
                                                             HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, request);
    }

    // Utilities
    private static ResponseEntity<ErrorResponseDTO> build(HttpStatus status, String message,
                                                          HttpServletRequest request) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponseDTO.of(status, message, request.getRequestURI()));
    }

    // Rejected parameters for validated methods.
    private static List<FieldErrorDTO> toFieldErrors(ParameterValidationResult result) {
        if (result instanceof ParameterErrors errors) {
            return errors.getFieldErrors().stream()
                    .map(GlobalExceptionHandler::toFieldError)
                    .toList();
        }

        String name = result.getMethodParameter().getParameterName();
        return result.getResolvableErrors().stream()
                .map(error -> new FieldErrorDTO(
                        name != null ? name : "parametro",
                        resolvableMessage(error)))
                .toList();
    }

    private static String resolvableMessage(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "Valor invalido.";
    }

    // Validation of the user (user authentication) for user request.
    private static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static FieldErrorDTO toFieldError(FieldError error) {
        String message = error.getDefaultMessage() != null
                ? error.getDefaultMessage()
                : "Valor invalido.";
        return new FieldErrorDTO(error.getField(), message);
    }

    // Only last property path violation remains.
    private static String lastNode(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }
}