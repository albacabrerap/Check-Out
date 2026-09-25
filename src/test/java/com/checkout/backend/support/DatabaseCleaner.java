package com.checkout.backend.support;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Vacia la base entre tests.
 *
 * Existe para poder quitar @Transactional de las clases de test que prueban
 * flujos financieros de escritura, y merece la pena explicar por que hacia falta
 * quitarlo.
 *
 * @Transactional en la clase de test es cómodo: cada test corre dentro de una
 * transaccion que se deshace al terminar, y la base queda limpia sin escribir
 * nada. El problema es que entonces el metodo de test es el dueno de la
 * transaccion externa, y los @Transactional de los servicios se unen a ella como
 * transacciones participantes. Eso cambia la semantica que se esta probando: en
 * produccion el metodo del servicio ES la transaccion externa, y el commit
 * comprueba la marca de rollback global que una transaccion participante fallida
 * deja puesta. Envuelto en la transaccion del test, ese commit no ocurre y el
 * fallo desaparece.
 *
 * Eso es exactamente lo que dejo pasar el 500 de las ordenes rechazadas: el test
 * que lo cubria pasaba en verde y el endpoint devolvia UnexpectedRollbackException
 * en produccion. Un test que no reproduce los limites transaccionales no puede
 * verificar el comportamiento transaccional.
 *
 * Asi que los flujos de escritura corren sin transaccion de test, commiteando de
 * verdad, y la limpieza se hace aqui.
 */
public final class DatabaseCleaner {

    private DatabaseCleaner() {
    }

    /**
     * Trunca todas las tablas y reinicia los contadores de identidad.
     *
     * Desactiva la integridad referencial mientras lo hace para no tener que
     * mantener a mano el orden de borrado de diecinueve tablas: ese orden es una
     * lista que se queda obsoleta en cuanto alguien anade una entidad, y cuando se
     * queda obsoleta el sintoma es un test que falla por una razon que no tiene
     * nada que ver con lo que prueba.
     *
     * Es especifico de H2, que es la base sobre la que corren los tests por
     * configuracion de src/test/resources/application.properties.
     */
    public static void clean(JdbcTemplate jdbc) {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            List<String> tables = jdbc.queryForList(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = 'PUBLIC' AND table_type = 'BASE TABLE'",
                    String.class);

            for (String table : tables) {
                jdbc.execute("TRUNCATE TABLE \"" + table + "\" RESTART IDENTITY");
            }
        } finally {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}
