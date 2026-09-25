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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce cualquier excepcion que salga de un controller a una respuesta JSON
 * con la forma de ErrorResponseDTO.
 *
 * Sin esta clase Spring responde los errores con su propio formato por defecto,
 * y las excepciones no previstas salen como un 500 con un cuerpo que incluye el
 * nombre de la clase Java que fallo. El front tendria que parsear dos formas
 * distintas de error segun quien lo genero, y los detalles internos se filtran.
 *
 * Dos reglas que se siguen en todos los handlers de abajo:
 *
 * 1. El mensaje de un 4xx describe que hizo mal el cliente; el de un 5xx es
 *    siempre generico. Un 500 significa que el problema es nuestro, y el
 *    mensaje real de la excepcion puede traer nombres de tablas, rutas o
 *    fragmentos de query. Eso va al log, no al cuerpo de la respuesta.
 *
 * 2. Solo se loguea con stack trace lo que un desarrollador tiene que ir a
 *    mirar. Un 404 o un 400 son operacion normal: se registran en DEBUG. Un 500
 *    es un defecto y va en ERROR.
 *
 * Alcance: un @RestControllerAdvice solo ve lo que pasa por el
 * DispatcherServlet. Lo que falla antes, dentro de la cadena de filtros de
 * Spring Security (por ejemplo un JWT invalido rechazado por el filtro de
 * autenticacion), nunca llega hasta aca; ese caso se cubre con un
 * AuthenticationEntryPoint y un AccessDeniedHandler propios, que pertenecen a
 * la configuracion de seguridad.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Mensaje unico para todos los 500. Ver la regla 1 del javadoc de la clase.
     */
    private static final String INTERNAL_ERROR_MESSAGE =
            "Ocurrio un error interno. Intentalo de nuevo mas tarde.";

    // ---------------------------------------------------------------------
    // Excepciones propias
    // ---------------------------------------------------------------------

    /**
     * Cubre de una sola vez toda la jerarquia de ApiException, porque cada
     * subclase ya sabe con que codigo quiere salir. Agregar una excepcion de
     * negocio nueva no requiere tocar esta clase.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponseDTO> handleApiException(ApiException ex,
                                                               HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    // ---------------------------------------------------------------------
    // 400 - el request no es utilizable
    // ---------------------------------------------------------------------

    /**
     * Falla un @Valid sobre un @RequestBody.
     *
     * Es el 400 mas frecuente de la API y el unico que aprovecha fieldErrors:
     * el cuerpo enumera cada campo rechazado con su motivo, para que el front
     * pueda marcar los inputs en vez de mostrar un cartel generico.
     */
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

    /**
     * Falla una restriccion declarada directamente sobre un parametro del
     * controller: un @Min en un @RequestParam, un @NotBlank en un @PathVariable.
     *
     * Desde Spring 6.1 este caso NO lanza ConstraintViolationException. El
     * framework valida los parametros el mismo y lanza esta excepcion, asi que
     * sin este handler el 400 se cae al handler de Exception y sale como 500.
     *
     * HandlerMethodValidationException hereda de ResponseStatusException, y su
     * status ya es 400. Se lo maneja aparte igual porque el handler generico de
     * ResponseStatusException no puede armar fieldErrors, y ese detalle es justo
     * lo que el cliente necesita para saber que parametro corregir.
     */
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

    /**
     * Falla una restriccion declarada fuera de un @RequestBody: un @Min sobre un
     * bean anotado con @Validated fuera de la capa web, tipicamente un service.
     * Esas violaciones no pasan por el mecanismo de arriba: las lanza el proxy de
     * validacion de Spring, no el framework web, y no vienen agrupadas en un
     * BindingResult, asi que el detalle por campo hay que armarlo desde el
     * property path de cada violacion.
     */
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

    /**
     * El cuerpo no se pudo deserializar: JSON mal formado, un enum con un valor
     * que no existe, un numero donde se esperaba una fecha.
     *
     * El mensaje de Jackson no se reenvia: incluye la clase Java destino y la
     * posicion en el stream, que no le sirven a quien consume la API y si
     * describen la estructura interna.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        log.debug("Cuerpo ilegible en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no se pudo leer. Revisa que sea JSON valido.", request);
    }

    /**
     * Falta un @RequestParam obligatorio.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Falta el parametro obligatorio '" + ex.getParameterName() + "'.", request);
    }

    /**
     * Falta un @RequestHeader obligatorio.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingHeader(MissingRequestHeaderException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Falta la cabecera obligatoria '" + ex.getHeaderName() + "'.", request);
    }

    /**
     * Red para el resto de los valores de request obligatorios que puedan faltar
     * (cookie, matrix variable, parte de un multipart).
     *
     * Los dos handlers de arriba son mas especificos y Spring los sigue
     * eligiendo cuando corresponde; este solo atrapa lo que ellos no cubren, de
     * modo que un valor faltante nuevo salga 400 en vez de caer al 500.
     */
    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingRequestValue(MissingRequestValueException ex,
                                                                      HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Falta un valor obligatorio en la solicitud.", request);
    }

    /**
     * Falta una path variable declarada en la firma del metodo.
     *
     * Es el unico MissingRequestValueException que NO es culpa del cliente:
     * significa que el metodo pide una variable que su propio @GetMapping no
     * declara. El cliente no puede corregirlo, asi que corresponde 500, y este
     * handler existe para que la subclase no herede el 400 del anterior.
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingPathVariable(MissingPathVariableException ex,
                                                                      HttpServletRequest request) {
        log.error("Mapping invalido en {}: falta la path variable '{}' en la plantilla de la URI",
                request.getRequestURI(), ex.getVariableName(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, request);
    }

    /**
     * Un parametro llego con un tipo que no se puede convertir, tipicamente
     * /usuarios/abc cuando el id es Long.
     *
     * Nombrar el parametro y el valor recibido es seguro: los dos los mando el
     * cliente. El tipo esperado se saca del metodo del controller, y puede venir
     * en null cuando el parametro no esta tipado, de ahi el chequeo.
     */
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

    // ---------------------------------------------------------------------
    // 401 / 403 - seguridad
    // ---------------------------------------------------------------------

    /**
     * Fallo de autenticacion levantado dentro de un controller, por ejemplo un
     * BadCredentialsException del AuthenticationManager en el endpoint de login.
     *
     * El mensaje no distingue entre correo inexistente y contrasena incorrecta.
     * Hacerlo convierte al login en un verificador de que cuentas existen.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthentication(AuthenticationException ex,
                                                                 HttpServletRequest request) {
        log.debug("Autenticacion fallida en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Credenciales invalidas.", request);
    }

    /**
     * El usuario esta autenticado y no tiene permiso. Es la excepcion que lanza
     * @PreAuthorize cuando la expresion da false.
     *
     * Atrapandola aca el 403 sale con el mismo formato que el resto de los
     * errores, en vez del cuerpo por defecto del contenedor.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        if (!isAuthenticated()) {
            // Sin identidad no corresponde 403. Ese codigo afirma "se quien eres
            // y no te alcanza", y aca no se sabe quien es: el cliente tiene que
            // autenticarse, no pedir permisos. Es la misma distincion que hace
            // Spring Security en su ExceptionTranslationFilter, que no llega a
            // intervenir cuando el advice atrapa la excepcion primero.
            log.debug("Acceso anonimo rechazado en {}", request.getRequestURI());
            return build(HttpStatus.UNAUTHORIZED,
                    "Debes autenticarte para acceder a este recurso.", request);
        }
        log.debug("Acceso denegado en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta accion.", request);
    }

    // ---------------------------------------------------------------------
    // 404 / 405 - la ruta
    // ---------------------------------------------------------------------

    /**
     * No hay ningun handler para la URL pedida. Sin esto Spring responde su
     * propia pagina de error y el 404 por ruta inexistente tendria otra forma
     * que el 404 por recurso inexistente.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoResourceFound(NoResourceFoundException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "La ruta solicitada no existe.", request);
    }

    /**
     * No hay handler y tampoco resource handler que atienda la URL. Cual de las
     * dos excepciones sale depende de como este configurado el manejo de
     * recursos estaticos, asi que se cubren las dos para que el 404 por ruta
     * inexistente no dependa de esa configuracion.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoHandlerFound(NoHandlerFoundException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "La ruta solicitada no existe.", request);
    }

    /**
     * La ruta existe pero no para ese verbo, por ejemplo un DELETE contra un
     * endpoint que solo define GET.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                     HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "El metodo " + ex.getMethod() + " no esta permitido en esta ruta.", request);
    }

    // ---------------------------------------------------------------------
    // 406 / 415 - negociacion de contenido
    // ---------------------------------------------------------------------

    /**
     * El Content-Type del cuerpo no es uno que la API pueda leer, tipicamente un
     * POST con text/plain contra un endpoint que espera JSON.
     *
     * Sin este handler el caso caia en el de Exception y salia 500, que le dice
     * al cliente que el servidor se rompio cuando en realidad mando mal la
     * cabecera. Se listan los tipos aceptados porque es exactamente el dato que
     * necesita para corregirlo.
     */
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

    /**
     * El cliente pidio en su Accept un formato que la API no produce.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponseDTO> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                                         HttpServletRequest request) {
        return build(HttpStatus.NOT_ACCEPTABLE,
                "Esta API solo responde en formato JSON.", request);
    }

    // ---------------------------------------------------------------------
    // 409 - conflicto con el estado guardado
    // ---------------------------------------------------------------------

    /**
     * Salto un constraint de la base: un UNIQUE o una foreign key.
     *
     * Es la red de seguridad del chequeo que hace el service. Cuando dos
     * registros con el mismo correo entran a la vez, los dos pasan la validacion
     * previa y el UNIQUE decide; ese perdedor termina aca y recibe el mismo 409
     * que si hubiera llegado solo.
     *
     * Va en WARN y no en DEBUG: puede ser una carrera normal, pero tambien una
     * validacion faltante en el service, y en ese caso conviene verlo.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                HttpServletRequest request) {
        log.warn("Violacion de integridad en {}", request.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT,
                "La operacion entra en conflicto con datos ya registrados.", request);
    }

    /**
     * El archivo subido supera el limite configurado.
     *
     * 413 es el codigo que existe exactamente para esto y le dice al cliente que
     * el problema es el tamano, no el contenido. Sin este handler la excepcion
     * cae en el de Exception y sale un 500, que sugiere un fallo del servidor
     * cuando basta con mandar un archivo mas pequeno.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                                 HttpServletRequest request) {
        log.debug("Subida rechazada por tamano en {}", request.getRequestURI());
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "El archivo supera el tamano maximo permitido.", request);
    }

    // ---------------------------------------------------------------------
    // 502 / 503 - el correo
    // ---------------------------------------------------------------------

    /**
     * El servidor de correo rechazo el envio o no respondio.
     *
     * 502 y no 500: el fallo no esta en esta aplicacion sino en un servicio del
     * que depende, y esa distincion importa para quien mira los logs o una
     * alerta. El 500 dice "nuestro codigo se rompio"; el 502 dice "el de al lado
     * no contesto", que se investiga en otro sitio.
     *
     * El mensaje es fijo y no el de la excepcion. Aqui se relaja el criterio de
     * mensaje generico para 5xx solo porque este texto lo escribimos nosotros y
     * no puede traer nada interno; el detalle de SMTP, que si puede incluir el
     * servidor y la cuenta, se queda en el log.
     */
    @ExceptionHandler(EmailSenderException.class)
    public ResponseEntity<ErrorResponseDTO> handleEmailSender(EmailSenderException ex,
                                                              HttpServletRequest request) {
        log.error("Fallo el envio de correo en {}", request.getRequestURI(), ex);
        return build(HttpStatus.BAD_GATEWAY,
                "No se pudo enviar el correo en este momento.", request);
    }

    /**
     * La cola del executor de correo esta llena.
     *
     * 503 con esa semantica exacta: el servicio existe y funciona, pero ahora
     * mismo no puede aceptar mas trabajo. Es el unico codigo que le dice al
     * cliente que reintentar mas tarde tiene sentido, cosa que un 500 no dice.
     *
     * Va en WARN y no en ERROR: que la cola se llene es el mecanismo de
     * proteccion haciendo su trabajo, no un defecto. Si aparece seguido, lo que
     * hay que revisar es el tamano del pool.
     */
    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<ErrorResponseDTO> handleTaskRejected(TaskRejectedException ex,
                                                               HttpServletRequest request) {
        log.warn("Cola de tareas llena en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio esta ocupado. Intentalo de nuevo en unos minutos.", request);
    }

    // ---------------------------------------------------------------------
    // Excepciones que ya traen su codigo
    // ---------------------------------------------------------------------

    /**
     * Excepcion que ya declara con que status quiere salir, sea lanzada por
     * nosotros o por el propio framework.
     *
     * Sin este handler caia en el de Exception y se convertia en 500,
     * descartando el codigo que la excepcion traia. El status se respeta, pero
     * el mensaje solo se reenvia si es un 4xx: un 5xx con detalle propio entra en
     * la misma regla que el resto de los 500 y sale con el texto generico.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDTO> handleResponseStatus(ResponseStatusException ex,
                                                                 HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            // Codigo no estandar: no hay reason phrase que poner en el DTO.
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

    // ---------------------------------------------------------------------
    // 500 - todo lo demas
    // ---------------------------------------------------------------------

    /**
     * Ultimo recurso: cualquier excepcion que no matcheo con ningun handler de
     * arriba.
     *
     * Spring elige siempre el handler mas especifico, asi que tener este metodo
     * no oculta a los anteriores. Lo que si hace es garantizar que ninguna
     * excepcion inesperada salga con el formato por defecto ni arrastre su
     * mensaje interno al cliente. El stack trace completo va al log, que es
     * donde hay que buscarlo.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex,
                                                             HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, request);
    }

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    private static ResponseEntity<ErrorResponseDTO> build(HttpStatus status, String message,
                                                          HttpServletRequest request) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponseDTO.of(status, message, request.getRequestURI()));
    }

    /**
     * Saca los campos rechazados de un parametro que fallo la validacion de
     * metodo.
     *
     * Hay dos formas posibles. Si el parametro es un objeto (un @ModelAttribute,
     * un @RequestBody), el resultado es un ParameterErrors y trae FieldError, con
     * el nombre real de cada campo adentro. Si es un valor suelto (un
     * @RequestParam con @Min), no hay campos: el error es del parametro entero y
     * el nombre se toma de su firma.
     */
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

    /**
     * Si hay un usuario real detras del request.
     *
     * Un token anonimo no cuenta: Spring Security instala un
     * AnonymousAuthenticationToken cuando nadie se autentico, de modo que el
     * Authentication no es null y su isAuthenticated() devuelve true. Sin el
     * chequeo de tipo, todo request sin credenciales pareceria autenticado.
     */
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

    /**
     * Se queda con el ultimo tramo del property path de una violacion.
     *
     * Jakarta Validation lo entrega como "metodo.argumento.campo" — por ejemplo
     * "buscarUsuario.id" — y al cliente solo le interesa "id", que es el nombre
     * que el mismo mando.
     */
    private static String lastNode(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }

}
