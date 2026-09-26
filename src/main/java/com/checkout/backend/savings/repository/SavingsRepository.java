package com.checkout.backend.savings.repository;

import com.checkout.backend.savings.model.Savings;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a la tabla savings, que guarda un unico saldo por usuario.
 */
public interface SavingsRepository extends JpaRepository<Savings, Long> {

    /**
     * La tabla tiene UNIQUE sobre user_id, asi que esto devuelve como mucho una
     * fila. Es Optional y no Savings porque el registro se crea la primera vez
     * que el usuario lo necesita, no al registrarse.
     */
    Optional<Savings> findByUserId(Long userId);

    /**
     * La misma fila, pero tomada en exclusiva hasta que termine la transaccion.
     *
     * Es un {@code SELECT ... FOR UPDATE}. Sirve de punto unico de serializacion
     * para todo lo que consume saldo disponible: un gasto escribe
     * {@code currentBalance}, pero un aporte a una meta no toca esta fila — solo
     * sube el acumulado de la meta. Sin este bloqueo, dos aportes a metas
     * distintas leen el mismo disponible, los dos lo validan contra el saldo de
     * antes y los dos escriben en filas diferentes: el {@code @Version} de cada
     * meta no los coordina porque no compiten por la misma fila. El resultado es
     * comprometer mas dinero del que hay.
     *
     * Bloquear aqui los pone en fila: el segundo espera, vuelve a calcular el
     * disponible con el aporte del primero ya contado, y lo rechaza si ya no
     * alcanza. Es pesimista y no optimista a proposito — el usuario recibe un
     * "no te alcanza" determinista en vez de un conflicto que deba reintentar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Savings s where s.user.id = :userId")
    Optional<Savings> findByUserIdForUpdate(@Param("userId") Long userId);

}
