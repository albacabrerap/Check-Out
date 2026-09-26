package com.checkout.backend.token_wallet.refresh_token.service;

import com.checkout.backend.token_wallet.refresh_token.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revoca la familia de tokens de un usuario en una transaccion propia.
 *
 * Existe como bean aparte por dos razones, y las dos son necesarias.
 *
 * La primera es {@link Propagation#REQUIRES_NEW}. Cuando se detecta que un
 * refresh token ya revocado vuelve a presentarse, hay que hacer dos cosas
 * incompatibles entre si: dejar constancia de la revocacion y rechazar la
 * peticion con una excepcion. Si ambas ocurren en la misma transaccion, la
 * excepcion la marca para rollback y la revocacion se deshace con ella. El
 * ataque queda detectado en el log y sin ningun efecto en la base. Con una
 * transaccion propia la revocacion se confirma por su cuenta y sobrevive al
 * rollback de quien la llamo.
 *
 * La segunda es que Spring implementa {@code @Transactional} con un proxy
 * alrededor del bean. Una llamada de un metodo a otro del mismo objeto no pasa
 * por ese proxy, asi que un {@code REQUIRES_NEW} declarado dentro de
 * RefreshTokenService y llamado desde RefreshTokenService no abriria ninguna
 * transaccion nueva: seguiria corriendo en la de fuera y el arreglo seria
 * invisible. Tiene que ser otro bean para que la llamada cruce el proxy.
 */
@Component
public class RefreshTokenFamilyRevoker {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenFamilyRevoker.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenFamilyRevoker(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Revoca todos los tokens vivos del usuario, pase lo que pase despues.
     *
     * @return cuantos tokens quedaron revocados
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamily(Long userId) {
        int revoked = refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
        log.warn("Se revocaron {} tokens de refresco del usuario {} por reutilizacion.",
                revoked, userId);
        return revoked;
    }

}
