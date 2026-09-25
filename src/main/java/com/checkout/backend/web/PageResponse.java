package com.checkout.backend.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Una pagina de resultados, con su contrato escrito.
 *
 * Existe en vez de devolver directamente el Page de Spring Data por dos razones.
 *
 * La primera es que el JSON de Page no es un contrato: incluye el objeto Pageable
 * entero, campos como "first", "numberOfElements" y un "sort" anidado, y esa forma
 * ha cambiado entre versiones de Spring Data. Un frontend escrito contra ella se
 * rompe en una actualizacion de dependencias que nadie relaciono con la API. Boot
 * avisa de esto por consola precisamente porque pasa.
 *
 * La segunda es que devuelve mas de lo que hace falta. Cinco campos responden
 * todas las preguntas de una tabla paginada: que hay en esta pagina, en que pagina
 * estoy, de que tamano, cuantos elementos hay en total y cuantas paginas.
 *
 * El listado paginado es incompatible con el que devolvia un array pelado, y eso
 * es deliberado: es mejor decidirlo ahora, con el frontend sin escribir, que
 * cuando ya haya pantallas que asumen un array. Con un ano de uso,
 * /token-wallet/transactions serian cientos de filas en cada peticion.
 *
 * @param content       los elementos de esta pagina
 * @param page          numero de pagina, empezando en 0
 * @param size          tamano de pagina pedido
 * @param totalElements total de elementos que cumplen el filtro
 * @param totalPages    total de paginas con ese tamano
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
