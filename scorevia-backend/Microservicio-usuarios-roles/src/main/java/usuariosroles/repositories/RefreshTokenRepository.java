package usuariosroles.repositories;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import usuariosroles.entities.RefreshToken;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByJti(String jti);

    /**
     * Revoca UN refresh token concreto solo si seguía activo (rotación normal en /auth/refresh,
     * o revocación puntual en /auth/logout). Update atómico condicionado: si devuelve 0 filas,
     * es que ya estaba revocado (reuso de un token ya rotado, o doble logout/refresh concurrente).
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revocado = true WHERE r.jti = :jti AND r.revocado = false")
    int revocarSiActivo(@Param("jti") String jti);

    /**
     * Revoca TODAS las sesiones activas de un usuario. Se dispara cuando se detecta el reuso de
     * un refresh token ya rotado (indicio de robo): más vale forzar el re-login en todos los
     * dispositivos que dejar sesiones potencialmente comprometidas con vida.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revocado = true WHERE r.idUsuario = :idUsuario AND r.revocado = false")
    int revocarTodosDelUsuario(@Param("idUsuario") Integer idUsuario);

    /** Limpieza periódica: filas de tokens ya caducados, revocados o no, no aportan nada más. */
    long deleteByFechaExpiracionBefore(LocalDateTime limite);
}
